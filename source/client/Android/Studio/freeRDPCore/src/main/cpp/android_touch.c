/* Android native touch routing. MPL-2.0; see android_freerdp.c. */
#include <freerdp/config.h>
#include <freerdp/log.h>
#include <freerdp/settings.h>
#include "android_freerdp.h"
#include "android_touch.h"

#define TAG CLIENT_TAG("android.touch")

static BOOL apply_contact(ANDROID_TOUCH_SLOT* slots, const ANDROID_TOUCH_CONTACT* contact);

static void clear_contacts(ANDROID_TOUCH_STATE* state)
{
	ZeroMemory(state->contacts, sizeof(state->contacts));
	ZeroMemory(state->submitted, sizeof(state->submitted));
}

static UINT touch_state_changed(RdpeiClientContext* rdpei, UINT32 state)
{
	androidContext* context = rdpei ? rdpei->custom : nullptr;
	if (!context)
		return CHANNEL_RC_OK;
	EnterCriticalSection(&context->touch.lock);
	if (context->touch.rdpei == rdpei)
	{
		context->touch.state = state;
		if (state == RDPEI_TOUCH_STATE_UNAVAILABLE)
		{
			/* OnClose precedes the channel-disconnected publication, and plugin
			 * termination can free this interface. Drop it in the notification. */
			context->touch.rdpei = nullptr;
			rdpei->TouchStateChanged = nullptr;
			rdpei->custom = nullptr;
		}
		if (state != RDPEI_TOUCH_STATE_READY)
		{
			android_pen_channel_reset(context);
			context->touch.generation++;
			clear_contacts(&context->touch);
		}
	}
	LeaveCriticalSection(&context->touch.lock);
	return CHANNEL_RC_OK;
}

void android_touch_init(androidContext* context)
{
	InitializeCriticalSection(&context->touch.lock);
	context->touch.generation = 1;
	android_pen_init(context);
}

void android_touch_channel_disconnected(androidContext* context)
{
	EnterCriticalSection(&context->touch.lock);
	RdpeiClientContext* rdpei = context->touch.rdpei;
	if (rdpei && (rdpei->custom == context))
	{
		rdpei->TouchStateChanged = nullptr;
		rdpei->custom = nullptr;
	}
	context->touch.rdpei = nullptr;
	context->touch.state = RDPEI_TOUCH_STATE_UNAVAILABLE;
	context->touch.generation++;
	android_pen_channel_reset(context);
	clear_contacts(&context->touch);
	LeaveCriticalSection(&context->touch.lock);
}

void android_touch_uninit(androidContext* context)
{
	/* The caller has joined the RDP/channel thread before releasing its context. */
	android_touch_channel_disconnected(context);
	DeleteCriticalSection(&context->touch.lock);
}

void android_touch_channel_connected(androidContext* context, RdpeiClientContext* rdpei)
{
	android_touch_channel_disconnected(context);
	if (!rdpei || !rdpei->GetTouchState || !rdpei->TouchBegin || !rdpei->TouchUpdate ||
	    !rdpei->TouchEnd || !rdpei->TouchCancel)
		return;
	EnterCriticalSection(&context->touch.lock);
	context->touch.rdpei = rdpei;
	rdpei->custom = context;
	rdpei->TouchStateChanged = touch_state_changed;
	context->touch.state = rdpei->GetTouchState(rdpei);
	LeaveCriticalSection(&context->touch.lock);
}

UINT32 android_touch_get_state(androidContext* context)
{
	if (!context)
		return RDPEI_TOUCH_STATE_UNAVAILABLE;
	EnterCriticalSection(&context->touch.lock);
	const UINT32 state = context->touch.state;
	LeaveCriticalSection(&context->touch.lock);
	return state;
}

BOOL android_touch_submit_frame(freerdp* instance, size_t count, const INT32* ids,
                                 const INT32* actions, const INT32* xs, const INT32* ys)
{
	if (!instance || !instance->context || !instance->context->settings || !ids || !actions ||
	    !xs || !ys || !count || (count > ANDROID_TOUCH_MAX_CONTACTS))
		return FALSE;
	const UINT32 width = freerdp_settings_get_uint32(instance->context->settings, FreeRDP_DesktopWidth);
	const UINT32 height = freerdp_settings_get_uint32(instance->context->settings, FreeRDP_DesktopHeight);
	for (size_t x = 0; x < count; x++)
	{
		if ((ids[x] < 0) || (actions[x] < ANDROID_TOUCH_BEGIN) ||
		    (actions[x] > ANDROID_TOUCH_CANCEL) || (xs[x] < 0) || (ys[x] < 0) ||
		    ((UINT32)xs[x] >= width) || ((UINT32)ys[x] >= height))
			return FALSE;
		for (size_t y = 0; y < x; y++)
		{
			if (ids[x] == ids[y])
				return FALSE;
		}
	}
	ANDROID_EVENT_TOUCH* event = calloc(1, sizeof(*event));
	if (!event)
		return FALSE;
	event->type = EVENT_TYPE_TOUCH_FRAME;
	event->count = count;
	for (size_t x = 0; x < count; x++)
	{
		event->contacts[x] = (ANDROID_TOUCH_CONTACT){ ids[x], actions[x], xs[x], ys[x] };
	}
	androidContext* context = (androidContext*)instance->context;
	EnterCriticalSection(&context->touch.lock);
	event->generation = context->touch.generation;
	ANDROID_TOUCH_SLOT projected[ANDROID_TOUCH_MAX_CONTACTS];
	memcpy(projected, context->touch.submitted, sizeof(projected));
	BOOL valid = (context->touch.state == RDPEI_TOUCH_STATE_READY);
	for (size_t x = 0; valid && (x < count); x++)
		valid = apply_contact(projected, &event->contacts[x]);
	const BOOL accepted = valid && android_push_event(instance, (ANDROID_EVENT*)event);
	if (accepted)
		memcpy(context->touch.submitted, projected, sizeof(projected));
	LeaveCriticalSection(&context->touch.lock);
	if (!accepted)
		android_event_free((ANDROID_EVENT*)event);
	return accepted;
}

BOOL android_touch_cancel(freerdp* instance)
{
	if (!instance || !instance->context)
		return FALSE;
	ANDROID_EVENT_TOUCH* event = calloc(1, sizeof(*event));
	if (!event)
		return FALSE;
	event->type = EVENT_TYPE_TOUCH_CANCEL;
	androidContext* context = (androidContext*)instance->context;
	EnterCriticalSection(&context->touch.lock);
	/* Drop every frame admitted before this cancellation, even if it is still queued. */
	event->generation = ++context->touch.generation;
	ZeroMemory(context->touch.submitted, sizeof(context->touch.submitted));
	const BOOL accepted = android_push_event(instance, (ANDROID_EVENT*)event);
	LeaveCriticalSection(&context->touch.lock);
	if (!accepted)
		android_event_free((ANDROID_EVENT*)event);
	return accepted;
}

static ANDROID_TOUCH_SLOT* find_contact(ANDROID_TOUCH_SLOT* slots, INT32 id)
{
	for (size_t x = 0; x < ANDROID_TOUCH_MAX_CONTACTS; x++)
	{
		if (slots[x].active && (slots[x].id == id))
			return &slots[x];
	}
	return nullptr;
}

static ANDROID_TOUCH_SLOT* free_contact(ANDROID_TOUCH_SLOT* slots)
{
	for (size_t x = 0; x < ANDROID_TOUCH_MAX_CONTACTS; x++)
	{
		if (!slots[x].active)
			return &slots[x];
	}
	return nullptr;
}

static BOOL apply_contact(ANDROID_TOUCH_SLOT* slots, const ANDROID_TOUCH_CONTACT* contact)
{
	ANDROID_TOUCH_SLOT* slot = find_contact(slots, contact->id);
	if (contact->action == ANDROID_TOUCH_BEGIN)
	{
		if (slot || !(slot = free_contact(slots)))
			return FALSE;
		*slot = (ANDROID_TOUCH_SLOT){ TRUE, contact->id, contact->x, contact->y };
	}
	else
	{
		if (!slot)
			return FALSE;
		slot->x = contact->x;
		slot->y = contact->y;
		if ((contact->action == ANDROID_TOUCH_END) || (contact->action == ANDROID_TOUCH_CANCEL))
			slot->active = FALSE;
	}
	return TRUE;
}

static BOOL cancel_contacts(ANDROID_TOUCH_STATE* state)
{
	BOOL ok = TRUE;
	for (size_t x = 0; x < ANDROID_TOUCH_MAX_CONTACTS; x++)
	{
		ANDROID_TOUCH_SLOT* slot = &state->contacts[x];
		if (!slot->active)
			continue;
		INT32 contactId = -1;
		if (state->rdpei->TouchCancel(state->rdpei, slot->id, slot->x, slot->y, &contactId) != CHANNEL_RC_OK)
			ok = FALSE;
	}
	ZeroMemory(state->contacts, sizeof(state->contacts));
	return ok;
}

BOOL android_touch_process_frame(androidContext* context, const ANDROID_EVENT_TOUCH* event)
{
	BOOL ok = TRUE;
	ANDROID_TOUCH_STATE* state = &context->touch;
	EnterCriticalSection(&state->lock);
	/* Losing a capability or replacing a channel is not a connection error. It is
	 * also never permission to reinterpret a native contact as a mouse click. */
	if ((event->generation != state->generation) || !state->rdpei ||
	    (state->state != RDPEI_TOUCH_STATE_READY))
		goto out;
	if (event->type == EVENT_TYPE_TOUCH_CANCEL)
	{
		ok = cancel_contacts(state);
		goto out;
	}
	ANDROID_TOUCH_SLOT projected[ANDROID_TOUCH_MAX_CONTACTS];
	memcpy(projected, state->contacts, sizeof(projected));
	/* Validate the complete batch before any RDPEI callback can observe it. */
	for (size_t x = 0; x < event->count; x++)
	{
		if (!apply_contact(projected, &event->contacts[x]))
		{
			ok = FALSE;
			goto fail;
		}
	}
	for (size_t x = 0; x < event->count; x++)
	{
		const ANDROID_TOUCH_CONTACT* contact = &event->contacts[x];
		const pcRdpeiTouchEvent senders[] = { state->rdpei->TouchBegin, state->rdpei->TouchUpdate,
		                                      state->rdpei->TouchEnd, state->rdpei->TouchCancel };
		INT32 contactId = -1;
		/* Keep a just-begun contact available for cancellation if its send fails. */
		if (contact->action == ANDROID_TOUCH_BEGIN)
			(void)apply_contact(state->contacts, contact);
		const UINT rc = senders[contact->action](state->rdpei, contact->id, contact->x, contact->y, &contactId);
		if ((rc != CHANNEL_RC_OK) || (contactId < 0))
		{
			ok = FALSE;
			goto fail;
		}
		if (contact->action != ANDROID_TOUCH_BEGIN)
			(void)apply_contact(state->contacts, contact);
	}
	goto out;
fail:
	(void)cancel_contacts(state);
	ZeroMemory(state->submitted, sizeof(state->submitted));
	state->generation++;
	WLog_WARN(TAG, "Native touch batch rejected; contact stream cancelled");
out:
	if (!ok)
	{
		/* Do not disconnect an otherwise healthy session for a rejected gesture. */
		WLog_WARN(TAG, "Native touch dispatch did not complete");
	}
	LeaveCriticalSection(&state->lock);
	return TRUE;
}
