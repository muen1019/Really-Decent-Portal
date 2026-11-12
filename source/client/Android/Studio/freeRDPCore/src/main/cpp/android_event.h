/**
 * FreeRDP: A Remote Desktop Protocol Implementation
 * Android Event System
 *
 * Copyright 2010-2012 Marc-Andre Moreau <marcandre.moreau@gmail.com>
 * Modified 2026-11-05: source-only queue merge candidate for FreeRDP 3.33.0.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#ifndef FREERDP_CLIENT_ANDROID_EVENT_H
#define FREERDP_CLIENT_ANDROID_EVENT_H
#include <freerdp/freerdp.h>
#include <freerdp/api.h>
#include <winpr/synch.h>
#include "android_touch.h"
#include "android_pen.h"

#define EVENT_TYPE_KEY 1
#define EVENT_TYPE_CURSOR 2
#define EVENT_TYPE_DISCONNECT 3
#define EVENT_TYPE_KEY_UNICODE 4
#define EVENT_TYPE_CLIPBOARD 5
#define EVENT_TYPE_TOUCH_FRAME 6
#define EVENT_TYPE_TOUCH_CANCEL 7
#define EVENT_TYPE_CURSOR_EXTENDED 8
#define EVENT_TYPE_PEN_FRAME 9
#define EVENT_TYPE_PEN_CANCEL 10

typedef struct
{
	int type;
} ANDROID_EVENT;

typedef struct
{
	int type;
	int flags;
	UINT16 scancode;
} ANDROID_EVENT_KEY;

typedef struct
{
	int type;
	UINT16 flags;
	UINT16 x;
	UINT16 y;
} ANDROID_EVENT_CURSOR;

typedef struct
{
	int type;
	void* data;
	int data_length;
	char* mimeType;
} ANDROID_EVENT_CLIPBOARD;

typedef struct android_event_touch
{
	int type;
	UINT64 generation;
	size_t count;
	ANDROID_TOUCH_CONTACT contacts[ANDROID_TOUCH_MAX_CONTACTS];
} ANDROID_EVENT_TOUCH;

typedef struct android_event_pen
{
	int type;
	UINT64 generation;
	INT32 action;
	INT32 x;
	INT32 y;
	INT32 pressure;
	UINT32 penFlags;
} ANDROID_EVENT_PEN;

typedef struct
{
	size_t size;
	size_t count;
	HANDLE isSet;
	CRITICAL_SECTION lock;
	ANDROID_EVENT** events;
	BOOL inputSuspended;
	/* Worker-owned deadline: no timer object/thread or callback lifetime. */
	BOOL wheelHorizontalSent;
	UINT64 wheelLastHorizontalMs;
	UINT64 wheelDueMs;
} ANDROID_EVENT_QUEUE;

/* TRUE transfers ownership to the queue (including a coalesced event); FALSE
 * leaves ownership with the caller. TRUE does not prove signalling or delivery. */
FREERDP_LOCAL BOOL android_push_event(freerdp* inst, ANDROID_EVENT* event);
/* Pause input and retire queued events without replacing the worker's wait handle. */
FREERDP_LOCAL void android_event_queue_set_suspended(freerdp* inst, BOOL suspended);

FREERDP_LOCAL HANDLE android_get_handle(freerdp* inst);
FREERDP_LOCAL BOOL android_check_handle(freerdp* inst);
/* Existing RDP worker wait hint; INFINITE when no wheel is deferred. */
FREERDP_LOCAL DWORD android_get_wait_timeout(freerdp* inst);

FREERDP_LOCAL ANDROID_EVENT_KEY* android_event_key_new(int flags, UINT16 scancode);
FREERDP_LOCAL ANDROID_EVENT_KEY* android_event_unicodekey_new(UINT16 flags, UINT16 key);
FREERDP_LOCAL ANDROID_EVENT_CURSOR* android_event_cursor_new(UINT16 flags, UINT16 x, UINT16 y);
FREERDP_LOCAL ANDROID_EVENT_CURSOR* android_event_extended_cursor_new(UINT16 flags, UINT16 x,
                                                                    UINT16 y);
FREERDP_LOCAL ANDROID_EVENT* android_event_disconnect_new(void);
FREERDP_LOCAL ANDROID_EVENT_CLIPBOARD*
android_event_clipboard_new(const void* data, size_t data_length, const char* mimeType);

FREERDP_LOCAL void android_event_free(ANDROID_EVENT* event);

FREERDP_LOCAL BOOL android_event_queue_init(freerdp* inst);
FREERDP_LOCAL void android_event_queue_uninit(freerdp* inst);

#endif /* FREERDP_CLIENT_ANDROID_EVENT_H */
