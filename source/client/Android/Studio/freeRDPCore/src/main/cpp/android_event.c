/**
 * FreeRDP: A Remote Desktop Protocol Implementation
 * Android Event System
 *
 * Copyright 2010-2012 Marc-Andre Moreau <marcandre.moreau@gmail.com>
 * Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz
 * Modified 2026-11-07: bounded wheel frames with nonblocking H-to-V deadline.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#include <freerdp/config.h>

#include <winpr/crt.h>
#include <winpr/sysinfo.h>

#include <freerdp/freerdp.h>
#include <freerdp/input.h>
#include <freerdp/log.h>

#define TAG CLIENT_TAG("android")

#include "android_freerdp.h"
#include "android_cliprdr.h"

/* Only adjacent ordinary cursor events with exactly PTR_FLAGS_MOVE may merge.
 * Caller holds context.eventLock and queue.lock, in that order. */
WINPR_ATTR_NODISCARD
static BOOL android_coalesce_cursor_move(ANDROID_EVENT_QUEUE* queue, const ANDROID_EVENT* event)
{
	WINPR_ASSERT(queue);
	WINPR_ASSERT(event);
	if ((queue->count < 1) || (event->type != EVENT_TYPE_CURSOR))
		return FALSE;
	const ANDROID_EVENT_CURSOR* cursor = (const ANDROID_EVENT_CURSOR*)event;
	if (cursor->flags != PTR_FLAGS_MOVE)
		return FALSE;
	ANDROID_EVENT* last = queue->events[queue->count - 1];
	if (!last || (last->type != EVENT_TYPE_CURSOR))
		return FALSE;
	ANDROID_EVENT_CURSOR* lastCursor = (ANDROID_EVENT_CURSOR*)last;
	if (lastCursor->flags != PTR_FLAGS_MOVE)
		return FALSE;
	lastCursor->x = cursor->x;
	lastCursor->y = cursor->y;
	return TRUE;
}

BOOL android_push_event(freerdp* inst, ANDROID_EVENT* event)
{
	if (!inst || !inst->context || !event)
		return FALSE;
	androidContext* aCtx = (androidContext*)inst->context;
	BOOL accepted = FALSE;
	BOOL coalesced = FALSE;
	/* The stable outer lock prevents queue detach/free while its inner lock is used. */
	EnterCriticalSection(&aCtx->eventLock);
	ANDROID_EVENT_QUEUE* queue = aCtx->event_queue;
	if (!queue)
		goto out_context;
	EnterCriticalSection(&queue->lock);
	if (queue->inputSuspended && event->type != EVENT_TYPE_DISCONNECT)
		goto out_queue;
	if (android_coalesce_cursor_move(queue, event))
	{
		accepted = TRUE;
		coalesced = TRUE;
		goto signal_queue;
	}
	if (queue->count >= queue->size)
	{
		const size_t max_size = SIZE_MAX / sizeof(ANDROID_EVENT*);
		size_t new_size = queue->size;
		do
		{
			/* Bound both addition and the byte multiplication passed to realloc. */
			if (new_size > max_size - 128)
				goto out_queue;
			new_size += 128;
		} while (new_size <= queue->count);
		void* events = realloc(queue->events, sizeof(ANDROID_EVENT*) * new_size);
		if (!events)
			goto out_queue;
		queue->events = (ANDROID_EVENT**)events;
		queue->size = new_size;
	}
	queue->events[queue->count++] = event;
	accepted = TRUE;
	/* As in 3.33, ownership has transferred even if signalling fails. Never
	 * return FALSE here: every JNI producer frees an event on FALSE. Keep
	 * SetEvent under the same locks as the empty-queue ResetEvent below. */
signal_queue:
	if (!SetEvent(queue->isSet))
		WLog_WARN(TAG, "SetEvent failed");
out_queue:
	LeaveCriticalSection(&queue->lock);
out_context:
	LeaveCriticalSection(&aCtx->eventLock);
	if (coalesced)
		android_event_free(event);
	return accepted;
}

void android_event_queue_set_suspended(freerdp* inst, BOOL suspended)
{
	WINPR_ASSERT(inst);
	WINPR_ASSERT(inst->context);
	androidContext* context = (androidContext*)inst->context;
	EnterCriticalSection(&context->eventLock);
	ANDROID_EVENT_QUEUE* queue = context->event_queue;
	if (queue)
	{
		EnterCriticalSection(&queue->lock);
		queue->inputSuspended = suspended;
		if (suspended)
		{
			size_t kept = 0;
			for (size_t x = 0; x < queue->count; x++)
			{
				ANDROID_EVENT* event = queue->events[x];
				if (event->type == EVENT_TYPE_DISCONNECT)
					queue->events[kept++] = event;
				else
					android_event_free(event);
			}
			memset(queue->events + kept, 0, (queue->count - kept) * sizeof(ANDROID_EVENT*));
			queue->count = kept;
			queue->wheelHorizontalSent = FALSE;
			queue->wheelLastHorizontalMs = 0;
			queue->wheelDueMs = 0;
			if (!(kept ? SetEvent(queue->isSet) : ResetEvent(queue->isSet)))
				WLog_WARN(TAG, "Failed to update reconnect queue signal");
		}
		LeaveCriticalSection(&queue->lock);
	}
	LeaveCriticalSection(&context->eventLock);
}

/* Experimental transport gap, not a guarantee about remote message time. */
#define ANDROID_WHEEL_H_TO_V_GAP_MS 32U

static UINT16 android_pure_wheel_axis(const ANDROID_EVENT* event)
{
	if (!event || event->type != EVENT_TYPE_CURSOR)
		return 0;
	const UINT16 flags = ((const ANDROID_EVENT_CURSOR*)event)->flags;
	const UINT16 axes = flags & (PTR_FLAGS_WHEEL | PTR_FLAGS_HWHEEL);
	/* Exactly one axis plus signed 9-bit rotation. All other inputs are FIFO barriers. */
	if ((axes != PTR_FLAGS_WHEEL && axes != PTR_FLAGS_HWHEEL) ||
	    (flags & ~(PTR_FLAGS_WHEEL | PTR_FLAGS_HWHEEL | 0x01FFU)) || !(flags & 0x01FFU))
		return 0;
	return axes;
}

static void android_note_horizontal(androidContext* context)
{
	/* Send callback has returned; never hold event/queue locks across output. */
	const UINT64 now = GetTickCount64();
	EnterCriticalSection(&context->eventLock);
	ANDROID_EVENT_QUEUE* queue = context->event_queue;
	if (queue)
	{
		EnterCriticalSection(&queue->lock);
		queue->wheelHorizontalSent = TRUE;
		queue->wheelLastHorizontalMs = now;
		LeaveCriticalSection(&queue->lock);
	}
	LeaveCriticalSection(&context->eventLock);
}

/* A bounded FIFO-prefix frame. Only pure wheels may change inter-axis order;
 * each axis retains every individual signed packet. Never cross a barrier. */
#define ANDROID_WHEEL_FRAME_MAX 512U

WINPR_ATTR_NODISCARD
static BOOL android_pop_frame(androidContext* context, ANDROID_EVENT** events, size_t* count)
{
	BOOL success = TRUE;
	*count = 0;
	EnterCriticalSection(&context->eventLock);
	ANDROID_EVENT_QUEUE* queue = context->event_queue;
	if (queue)
	{
		EnterCriticalSection(&queue->lock);
		if (queue->count > 0)
		{
			size_t frameCount = 1;
			const BOOL wheelFrame = android_pure_wheel_axis(queue->events[0]) != 0;
			BOOL hasVertical = FALSE;
			if (wheelFrame)
			{
				frameCount = 0;
				while (frameCount < queue->count && frameCount < ANDROID_WHEEL_FRAME_MAX)
				{
					const UINT16 axis = android_pure_wheel_axis(queue->events[frameCount]);
					if (!axis) break;
					hasVertical = hasVertical || axis == PTR_FLAGS_WHEEL;
					frameCount++;
				}
			}
			const UINT64 now = GetTickCount64();
			if (hasVertical && queue->wheelHorizontalSent &&
			    now - queue->wheelLastHorizontalMs < ANDROID_WHEEL_H_TO_V_GAP_MS)
			{
				queue->wheelDueMs = queue->wheelLastHorizontalMs + ANDROID_WHEEL_H_TO_V_GAP_MS;
				/* Nothing is detached: the frame and all later barriers stay FIFO-owned. */
				if (!ResetEvent(queue->isSet))
				{
					WLog_ERR(TAG, "Wheel deadline ResetEvent failed");
					success = FALSE;
				}
				goto out_frame;
			}
			queue->wheelDueMs = 0;
			if (wheelFrame)
			{
				/* Stable partition: V packets first, then H packets. No summing. */
				for (size_t x = 0; x < frameCount; x++)
					if (android_pure_wheel_axis(queue->events[x]) == PTR_FLAGS_WHEEL)
						events[(*count)++] = queue->events[x];
				for (size_t x = 0; x < frameCount; x++)
					if (android_pure_wheel_axis(queue->events[x]) == PTR_FLAGS_HWHEEL)
						events[(*count)++] = queue->events[x];
			}
			else
				events[(*count)++] = queue->events[0];
			queue->count -= frameCount;
			memmove(queue->events, queue->events + frameCount,
			        queue->count * sizeof(ANDROID_EVENT*));
			memset(queue->events + queue->count, 0, frameCount * sizeof(ANDROID_EVENT*));
		}
		if (queue->count == 0 && !ResetEvent(queue->isSet))
			WLog_WARN(TAG, "ResetEvent failed");
out_frame:
		LeaveCriticalSection(&queue->lock);
	}
	LeaveCriticalSection(&context->eventLock);
	/* The caller owns exactly *count detached nodes; no lock spans output. */
	return success;
}

WINPR_ATTR_NODISCARD
static BOOL android_process_event(freerdp* inst)
{
	WINPR_ASSERT(inst);
	rdpContext* context = inst->context;
	WINPR_ASSERT(context);
	androidContext* afc = (androidContext*)context;
	ANDROID_EVENT* events[ANDROID_WHEEL_FRAME_MAX];
	for (;;)
	{
		size_t count = 0;
		if (!android_pop_frame(afc, events, &count))
			return FALSE;
		if (count == 0)
			break;
		for (size_t index = 0; index < count; index++)
		{
			BOOL rc = FALSE;
			ANDROID_EVENT* event = events[index];
			switch (event->type)
			{
				case EVENT_TYPE_KEY:
				{
					ANDROID_EVENT_KEY* key_event = (ANDROID_EVENT_KEY*)event;

					rc = freerdp_input_send_keyboard_event(context->input, key_event->flags,
					                                       key_event->scancode);
				}
				break;

				case EVENT_TYPE_KEY_UNICODE:
				{
					ANDROID_EVENT_KEY* key_event = (ANDROID_EVENT_KEY*)event;

					rc = freerdp_input_send_unicode_keyboard_event(context->input, key_event->flags,
					                                               key_event->scancode);
				}
				break;

				case EVENT_TYPE_CURSOR:
				{
					ANDROID_EVENT_CURSOR* cursor_event = (ANDROID_EVENT_CURSOR*)event;

					rc = freerdp_input_send_mouse_event(context->input, cursor_event->flags,
					                                    cursor_event->x, cursor_event->y);
					if (rc && android_pure_wheel_axis(event) == PTR_FLAGS_HWHEEL)
						android_note_horizontal(afc);
				}
				break;

				case EVENT_TYPE_CURSOR_EXTENDED:
				{
					ANDROID_EVENT_CURSOR* cursor_event = (ANDROID_EVENT_CURSOR*)event;
					rc = freerdp_input_send_extended_mouse_event(context->input, cursor_event->flags,
					                                             cursor_event->x, cursor_event->y);
				}
				break;

				case EVENT_TYPE_CLIPBOARD:
				{
					ANDROID_EVENT_CLIPBOARD* clipboard_event = (ANDROID_EVENT_CLIPBOARD*)event;
					const char* mimeType = clipboard_event->mimeType;
					UINT32 formatId = ClipboardRegisterFormat(afc->clipboard, mimeType);
					UINT32 size = clipboard_event->data_length;

					if (size)
						ClipboardSetData(afc->clipboard, formatId, clipboard_event->data, size);
					else
						ClipboardEmpty(afc->clipboard);

					rc = (android_cliprdr_send_client_format_list(afc->cliprdr) == CHANNEL_RC_OK);
				}
				break;

				case EVENT_TYPE_TOUCH_FRAME:
				case EVENT_TYPE_TOUCH_CANCEL:
					rc = android_touch_process_frame(afc, (const ANDROID_EVENT_TOUCH*)event);
					break;

				case EVENT_TYPE_PEN_FRAME:
				case EVENT_TYPE_PEN_CANCEL:
					rc = android_pen_process(afc,(const ANDROID_EVENT_PEN*)event);
					break;

				case EVENT_TYPE_DISCONNECT:
				default:
					break;
			}

			android_event_free(event);
			if (!rc)
			{
				/* These nodes left the queue together, so failure also owns the tail. */
				for (size_t rest = index + 1; rest < count; rest++)
					android_event_free(events[rest]);
				return FALSE;
			}
		}
	}
	return TRUE;
}

HANDLE android_get_handle(freerdp* inst)
{
	if (!inst || !inst->context)
		return nullptr;
	androidContext* aCtx = (androidContext*)inst->context;
	EnterCriticalSection(&aCtx->eventLock);
	ANDROID_EVENT_QUEUE* queue = aCtx->event_queue;
	HANDLE handle = queue ? queue->isSet : nullptr;
	LeaveCriticalSection(&aCtx->eventLock);
	/* Borrowed by the RDP worker; client_free must still join that worker
	 * before uninit closes this handle or destroys context.eventLock. */
	return handle;
}

DWORD android_get_wait_timeout(freerdp* inst)
{
	if (!inst || !inst->context)
		return INFINITE;
	androidContext* context = (androidContext*)inst->context;
	DWORD timeout = INFINITE;
	EnterCriticalSection(&context->eventLock);
	ANDROID_EVENT_QUEUE* queue = context->event_queue;
	if (queue)
	{
		EnterCriticalSection(&queue->lock);
		if (queue->wheelDueMs)
		{
			const UINT64 now = GetTickCount64();
			timeout = now >= queue->wheelDueMs ? 0 : (DWORD)(queue->wheelDueMs - now);
		}
		LeaveCriticalSection(&queue->lock);
	}
	LeaveCriticalSection(&context->eventLock);
	return timeout;
}

BOOL android_check_handle(freerdp* inst)
{
	if (!inst || !inst->context)
		return FALSE;
	androidContext* aCtx = (androidContext*)inst->context;
	DWORD status = WAIT_FAILED;
	BOOL deadlineReady = FALSE;
	EnterCriticalSection(&aCtx->eventLock);
	ANDROID_EVENT_QUEUE* queue = aCtx->event_queue;
	if (queue && queue->isSet)
	{
		EnterCriticalSection(&queue->lock);
		status = WaitForSingleObject(queue->isSet, 0);
		deadlineReady = queue->wheelDueMs && GetTickCount64() >= queue->wheelDueMs;
		LeaveCriticalSection(&queue->lock);
	}
	LeaveCriticalSection(&aCtx->eventLock);
	if (status == WAIT_OBJECT_0 || (status == WAIT_TIMEOUT && deadlineReady))
		return android_process_event(inst);
	return status == WAIT_TIMEOUT;
}

ANDROID_EVENT_KEY* android_event_key_new(int flags, UINT16 scancode)
{
	ANDROID_EVENT_KEY* event = (ANDROID_EVENT_KEY*)calloc(1, sizeof(ANDROID_EVENT_KEY));

	if (!event)
		return nullptr;

	event->type = EVENT_TYPE_KEY;
	event->flags = flags;
	event->scancode = scancode;
	return event;
}

static void android_event_key_free(ANDROID_EVENT_KEY* event)
{
	free(event);
}

ANDROID_EVENT_KEY* android_event_unicodekey_new(UINT16 flags, UINT16 key)
{
	ANDROID_EVENT_KEY* event;
	event = (ANDROID_EVENT_KEY*)calloc(1, sizeof(ANDROID_EVENT_KEY));

	if (!event)
		return nullptr;

	event->type = EVENT_TYPE_KEY_UNICODE;
	event->flags = flags;
	event->scancode = key;
	return event;
}

static void android_event_unicodekey_free(ANDROID_EVENT_KEY* event)
{
	free(event);
}

ANDROID_EVENT_CURSOR* android_event_cursor_new(UINT16 flags, UINT16 x, UINT16 y)
{
	ANDROID_EVENT_CURSOR* event;
	event = (ANDROID_EVENT_CURSOR*)calloc(1, sizeof(ANDROID_EVENT_CURSOR));

	if (!event)
		return nullptr;

	event->type = EVENT_TYPE_CURSOR;
	event->x = x;
	event->y = y;
	event->flags = flags;
	return event;
}

static void android_event_cursor_free(ANDROID_EVENT_CURSOR* event)
{
	free(event);
}

ANDROID_EVENT_CURSOR* android_event_extended_cursor_new(UINT16 flags, UINT16 x, UINT16 y)
{
	ANDROID_EVENT_CURSOR* event = android_event_cursor_new(flags, x, y);
	if (event)
		event->type = EVENT_TYPE_CURSOR_EXTENDED;
	return event;
}

ANDROID_EVENT* android_event_disconnect_new(void)
{
	ANDROID_EVENT* event;
	event = (ANDROID_EVENT*)calloc(1, sizeof(ANDROID_EVENT));

	if (!event)
		return nullptr;

	event->type = EVENT_TYPE_DISCONNECT;
	return event;
}

static void android_event_disconnect_free(ANDROID_EVENT* event)
{
	free(event);
}

ANDROID_EVENT_CLIPBOARD* android_event_clipboard_new(const void* data, size_t data_length,
                                                     const char* mimeType)
{
	ANDROID_EVENT_CLIPBOARD* event;
	event = (ANDROID_EVENT_CLIPBOARD*)calloc(1, sizeof(ANDROID_EVENT_CLIPBOARD));

	if (!event)
		return nullptr;

	event->type = EVENT_TYPE_CLIPBOARD;
	event->mimeType = mimeType ? _strdup(mimeType) : nullptr;

	if (mimeType && !event->mimeType)
	{
		free(event);
		return nullptr;
	}

	if (data && data_length > 0)
	{
		const BOOL isText = !mimeType || strcmp(mimeType, "text/plain;charset=utf-8") == 0;
		/* Text data needs a null terminator; image data is stored as-is. */
		event->data = isText ? calloc(data_length + 1, sizeof(char)) : malloc(data_length);

		if (!event->data)
		{
			free(event->mimeType);
			free(event);
			return nullptr;
		}

		memcpy(event->data, data, data_length);
		event->data_length = isText ? data_length + 1 : data_length;
	}

	return event;
}

static void android_event_clipboard_free(ANDROID_EVENT_CLIPBOARD* event)
{
	if (event)
	{
		free(event->data);
		free(event->mimeType);
		free(event);
	}
}

BOOL android_event_queue_init(freerdp* inst)
{
	if (!inst || !inst->context)
		return FALSE;
	androidContext* aCtx = (androidContext*)inst->context;
	ANDROID_EVENT_QUEUE* queue = (ANDROID_EVENT_QUEUE*)calloc(1, sizeof(ANDROID_EVENT_QUEUE));
	if (!queue)
	{
		WLog_ERR(TAG, "android_event_queue_init: memory allocation failed");
		return FALSE;
	}
	queue->size = 16;
	queue->isSet = CreateEventA(nullptr, TRUE, FALSE, nullptr);
	if (!queue->isSet)
	{
		free(queue);
		return FALSE;
	}
	queue->events = (ANDROID_EVENT**)calloc(queue->size, sizeof(ANDROID_EVENT*));
	if (!queue->events)
	{
		WLog_ERR(TAG, "android_event_queue_init: memory allocation failed");
		if (!CloseHandle(queue->isSet))
			WLog_WARN(TAG, "CloseHandle failed");
		free(queue);
		return FALSE;
	}
	if (!InitializeCriticalSectionAndSpinCount(&queue->lock, 4000))
	{
		WLog_ERR(TAG, "android_event_queue_init: failed to initialize lock");
		free(queue->events);
		if (!CloseHandle(queue->isSet))
			WLog_WARN(TAG, "CloseHandle failed");
		free(queue);
		return FALSE;
	}
	/* Context initialization creates eventLock before calling this function. */
	EnterCriticalSection(&aCtx->eventLock);
	const BOOL publish = (aCtx->event_queue == nullptr);
	if (publish)
		aCtx->event_queue = queue;
	LeaveCriticalSection(&aCtx->eventLock);
	if (!publish)
	{
		DeleteCriticalSection(&queue->lock);
		free(queue->events);
		if (!CloseHandle(queue->isSet))
			WLog_WARN(TAG, "CloseHandle failed");
		free(queue);
	}
	return publish;
}

void android_event_queue_uninit(freerdp* inst)
{
	if (!inst || !inst->context)
		return;
	androidContext* aCtx = (androidContext*)inst->context;
	/* Context destruction still joins the worker first and blocks late JNI
	 * access. The outer lock prevents any new queue-lock user after detach. */
	EnterCriticalSection(&aCtx->eventLock);
	ANDROID_EVENT_QUEUE* queue = aCtx->event_queue;
	aCtx->event_queue = nullptr;
	if (queue)
	{
		EnterCriticalSection(&queue->lock);
		for (size_t x = 0; x < queue->count; x++)
			android_event_free(queue->events[x]);
		free(queue->events);
		queue->events = nullptr;
		queue->size = 0;
		queue->count = 0;
		queue->wheelHorizontalSent = FALSE;
		queue->wheelLastHorizontalMs = 0;
		queue->wheelDueMs = 0;
		if (queue->isSet && !CloseHandle(queue->isSet))
			WLog_WARN(TAG, "CloseHandle failed");
		queue->isSet = nullptr;
		LeaveCriticalSection(&queue->lock);
		DeleteCriticalSection(&queue->lock);
		free(queue);
	}
	LeaveCriticalSection(&aCtx->eventLock);
}

void android_event_free(ANDROID_EVENT* event)
{
	if (!event)
		return;

	switch (event->type)
	{
		case EVENT_TYPE_KEY:
			android_event_key_free((ANDROID_EVENT_KEY*)event);
			break;

		case EVENT_TYPE_KEY_UNICODE:
			android_event_unicodekey_free((ANDROID_EVENT_KEY*)event);
			break;

		case EVENT_TYPE_CURSOR:
		case EVENT_TYPE_CURSOR_EXTENDED:
			android_event_cursor_free((ANDROID_EVENT_CURSOR*)event);
			break;

		case EVENT_TYPE_DISCONNECT:
			android_event_disconnect_free((ANDROID_EVENT*)event);
			break;

		case EVENT_TYPE_CLIPBOARD:
			android_event_clipboard_free((ANDROID_EVENT_CLIPBOARD*)event);
			break;

		case EVENT_TYPE_TOUCH_FRAME:
		case EVENT_TYPE_TOUCH_CANCEL:
		default:
			free(event);
			break;
	}
}
