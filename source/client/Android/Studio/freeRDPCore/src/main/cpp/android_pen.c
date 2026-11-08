/* Android PEN -> standard RDPEI, independent range/contact. MPL-2.0. */
#include <freerdp/config.h>
#include <freerdp/log.h>
#include <freerdp/settings.h>
#include "android_freerdp.h"
#include "android_pen.h"

#define TAG CLIENT_TAG("android.pen")

static UINT32 version_locked(const androidContext* context)
{
	RdpeiClientContext* rdpei = context->touch.rdpei;
	return rdpei && rdpei->GetVersion ? rdpei->GetVersion(rdpei) : 0;
}

static BOOL ready_locked(const androidContext* context)
{
	RdpeiClientContext* rdpei = context->touch.rdpei;
	return context->touch.state == RDPEI_TOUCH_STATE_READY && rdpei &&
	    version_locked(context) >= RDPINPUT_PROTOCOL_V200 && rdpei->AddPen && rdpei->PenRawEvent;
}

void android_pen_init(androidContext* context)
{
	context->pen.generation = 1;
}

void android_pen_channel_reset(androidContext* context)
{
	context->pen.generation++;
	context->pen.cancelRequested = FALSE;
	ZeroMemory(&context->pen.submitted,sizeof(context->pen.submitted));
	ZeroMemory(&context->pen.contact,sizeof(context->pen.contact));
	/* Pending payloads still occupy the real queue until its consumer frees them. */
}

UINT32 android_pen_get_version(androidContext* context)
{
	if (!context)
		return 0;
	EnterCriticalSection(&context->touch.lock);
	UINT32 version = version_locked(context);
	LeaveCriticalSection(&context->touch.lock);
	return version;
}

UINT32 android_pen_get_state(androidContext* context)
{
	if (!context)
		return RDPEI_TOUCH_STATE_UNAVAILABLE;
	EnterCriticalSection(&context->touch.lock);
	UINT32 state = context->touch.state;
	if (state == RDPEI_TOUCH_STATE_READY && !ready_locked(context))
		state = RDPEI_TOUCH_STATE_UNAVAILABLE;
	LeaveCriticalSection(&context->touch.lock);
	return state;
}

static BOOL project(ANDROID_PEN_SLOT* slot, const ANDROID_EVENT_PEN* event)
{
	if (event->action < ANDROID_PEN_BEGIN || event->action > ANDROID_PEN_HOVER_EXIT ||
	    event->pressure < 0 || event->pressure > 1024 ||
	    (event->penFlags & ~(RDPINPUT_PEN_FLAG_BARREL_PRESSED | RDPINPUT_PEN_FLAG_ERASER_PRESSED)))
		return FALSE;
	switch (event->action)
	{
	case ANDROID_PEN_BEGIN:
		if (slot->touching)
			return FALSE;
		slot->active = TRUE;
		slot->touching = TRUE;
		break;
	case ANDROID_PEN_UPDATE:
		if (!slot->active || !slot->touching)
			return FALSE;
		break;
	case ANDROID_PEN_END:
		if (!slot->active || !slot->touching || event->pressure != 0 ||
		    event->x != slot->x || event->y != slot->y)
			return FALSE;
		slot->touching = FALSE;
		break;
	case ANDROID_PEN_HOVER_ENTER:
		if (slot->active || event->pressure != 0)
			return FALSE;
		slot->active = TRUE;
		break;
	case ANDROID_PEN_HOVER_UPDATE:
		if (!slot->active || slot->touching || event->pressure != 0)
			return FALSE;
		break;
	case ANDROID_PEN_HOVER_EXIT:
		if (slot->touching)
			return FALSE;
		/* Fall through: both exits preserve the last reported position. */
	case ANDROID_PEN_CANCEL:
		if (!slot->active)
			return FALSE;
		if (event->pressure != 0 || event->x != slot->x || event->y != slot->y)
			return FALSE;
		slot->active = FALSE;
		slot->touching = FALSE;
		break;
	default:
		return FALSE;
	}
	slot->x = event->x;
	slot->y = event->y;
	slot->pressure = event->pressure;
	slot->penFlags = event->penFlags;
	return TRUE;
}

BOOL android_pen_submit(freerdp* instance, INT32 action, INT32 x, INT32 y,
                         INT32 pressure, UINT32 penFlags)
{
	if (!instance || !instance->context || !instance->context->settings)
		return FALSE;
	const UINT32 width = freerdp_settings_get_uint32(instance->context->settings,FreeRDP_DesktopWidth);
	const UINT32 height = freerdp_settings_get_uint32(instance->context->settings,FreeRDP_DesktopHeight);
	if (x < 0 || y < 0 || (UINT32)x >= width || (UINT32)y >= height)
		return FALSE;
	ANDROID_EVENT_PEN* event = calloc(1,sizeof(*event));
	if (!event)
		return FALSE;
	*event = (ANDROID_EVENT_PEN){ .type = EVENT_TYPE_PEN_FRAME, .action = action,
	    .x = x, .y = y, .pressure = pressure, .penFlags = penFlags };
	androidContext* context = (androidContext*)instance->context;
	EnterCriticalSection(&context->touch.lock);
	ANDROID_PEN_SLOT submitted = context->pen.submitted;
	event->generation = context->pen.generation;
	const BOOL accepted = ready_locked(context) &&
	    context->pen.pending < ANDROID_PEN_MAX_PENDING && project(&submitted,event) &&
	    android_push_event(instance,(ANDROID_EVENT*)event);
	if (accepted)
	{
		context->pen.submitted = submitted;
		context->pen.pending++;
	}
	LeaveCriticalSection(&context->touch.lock);
	if (!accepted)
		android_event_free((ANDROID_EVENT*)event);
	return accepted;
}

BOOL android_pen_cancel(freerdp* instance)
{
	if (!instance || !instance->context)
		return FALSE;
	ANDROID_EVENT_PEN* marker = NULL;
	androidContext* context = (androidContext*)instance->context;
	EnterCriticalSection(&context->touch.lock);
	context->pen.generation++;
	context->pen.cancelRequested = TRUE;
	ZeroMemory(&context->pen.submitted,sizeof(context->pen.submitted));
	BOOL accepted = TRUE;
	if (!context->pen.pending)
	{
		marker = calloc(1,sizeof(*marker));
		if (marker)
		{
			marker->type = EVENT_TYPE_PEN_CANCEL;
			marker->generation = context->pen.generation;
		}
		accepted = marker && android_push_event(instance,(ANDROID_EVENT*)marker);
		if (accepted)
		{
			context->pen.pending++;
			marker = NULL;
		}
	}
	LeaveCriticalSection(&context->touch.lock);
	free(marker);
	return accepted;
}

static BOOL send_cancel(androidContext* context)
{
	BOOL ok = TRUE;
	ANDROID_PEN_SLOT* contact = &context->pen.contact;
	if (contact->active && ready_locked(context))
	{
		RdpeiClientContext* rdpei = context->touch.rdpei;
		/* PenCancel is nil upstream; cancel the current range/contact phase. */
		const UINT32 flags = (contact->touching ? RDPINPUT_CONTACT_FLAG_UP :
		    RDPINPUT_CONTACT_FLAG_UPDATE) | RDPINPUT_CONTACT_FLAG_CANCELED;
		ok = rdpei->PenRawEvent(rdpei,0,flags,
		    RDPINPUT_PEN_CONTACT_PENFLAGS_PRESENT | RDPINPUT_PEN_CONTACT_PRESSURE_PRESENT,
		    contact->x,contact->y,contact->penFlags,(UINT32)0) == CHANNEL_RC_OK;
	}
	ZeroMemory(contact,sizeof(*contact));
	return ok;
}

BOOL android_pen_process(androidContext* context, const ANDROID_EVENT_PEN* event)
{
	if (!context || !event)
		return TRUE;
	EnterCriticalSection(&context->touch.lock);
	if (context->pen.pending)
		context->pen.pending--;
	if (context->pen.cancelRequested)
	{
		(void)send_cancel(context);
		context->pen.cancelRequested = FALSE;
	}
	if (event->generation != context->pen.generation || !ready_locked(context) ||
	    event->type == EVENT_TYPE_PEN_CANCEL)
		goto out;
	ANDROID_PEN_SLOT projected = context->pen.contact;
	const rdpSettings* settings = context->common.context.settings;
	if (!settings || event->x < 0 || event->y < 0 ||
	    (UINT32)event->x >= freerdp_settings_get_uint32(settings,FreeRDP_DesktopWidth) ||
	    (UINT32)event->y >= freerdp_settings_get_uint32(settings,FreeRDP_DesktopHeight) ||
	    !project(&projected,event))
		goto fail;
	const UINT32 flags[] = {
		RDPINPUT_CONTACT_FLAG_DOWN | RDPINPUT_CONTACT_FLAG_INRANGE | RDPINPUT_CONTACT_FLAG_INCONTACT,
		RDPINPUT_CONTACT_FLAG_UPDATE | RDPINPUT_CONTACT_FLAG_INRANGE | RDPINPUT_CONTACT_FLAG_INCONTACT,
		RDPINPUT_CONTACT_FLAG_UP | RDPINPUT_CONTACT_FLAG_INRANGE,
		(context->pen.contact.touching ? RDPINPUT_CONTACT_FLAG_UP :
		    RDPINPUT_CONTACT_FLAG_UPDATE) | RDPINPUT_CONTACT_FLAG_CANCELED,
		RDPINPUT_CONTACT_FLAG_UPDATE | RDPINPUT_CONTACT_FLAG_INRANGE,
		RDPINPUT_CONTACT_FLAG_UPDATE | RDPINPUT_CONTACT_FLAG_INRANGE,
		RDPINPUT_CONTACT_FLAG_UPDATE
	};
	RdpeiClientContext* rdpei = context->touch.rdpei;
	if (event->action == ANDROID_PEN_BEGIN || event->action == ANDROID_PEN_HOVER_ENTER)
		context->pen.contact = projected;
	if (rdpei->PenRawEvent(rdpei,0,flags[event->action],
	        RDPINPUT_PEN_CONTACT_PENFLAGS_PRESENT | RDPINPUT_PEN_CONTACT_PRESSURE_PRESENT,
	        event->x,event->y,event->penFlags,(UINT32)event->pressure) != CHANNEL_RC_OK)
		goto fail;
	context->pen.contact = projected;
	goto out;
fail:
	(void)send_cancel(context);
	context->pen.generation++;
	ZeroMemory(&context->pen.submitted,sizeof(context->pen.submitted));
	WLog_WARN(TAG,"Native pen frame rejected; only pen owner retired");
out:
	LeaveCriticalSection(&context->touch.lock);
	return TRUE; /* A rejected stroke must not disconnect a healthy RDP session. */
}
