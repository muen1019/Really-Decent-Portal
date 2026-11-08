/* Android native touch routing. MPL-2.0; see android_freerdp.c. */
#ifndef FREERDP_CLIENT_ANDROID_TOUCH_H
#define FREERDP_CLIENT_ANDROID_TOUCH_H

#include <freerdp/client/rdpei.h>
#include <winpr/synch.h>

#define ANDROID_TOUCH_MAX_CONTACTS 64
#define ANDROID_TOUCH_BEGIN 0
#define ANDROID_TOUCH_UPDATE 1
#define ANDROID_TOUCH_END 2
#define ANDROID_TOUCH_CANCEL 3

typedef struct
{
	INT32 id;
	INT32 action;
	INT32 x;
	INT32 y;
} ANDROID_TOUCH_CONTACT;

typedef struct
{
	BOOL active;
	INT32 id;
	INT32 x;
	INT32 y;
} ANDROID_TOUCH_SLOT;

typedef struct
{
	CRITICAL_SECTION lock;
	RdpeiClientContext* rdpei;
	UINT32 state;
	UINT64 generation;
	ANDROID_TOUCH_SLOT contacts[ANDROID_TOUCH_MAX_CONTACTS];
	ANDROID_TOUCH_SLOT submitted[ANDROID_TOUCH_MAX_CONTACTS];
} ANDROID_TOUCH_STATE;

struct android_context;
struct android_event_touch;

FREERDP_LOCAL void android_touch_init(struct android_context* context);
FREERDP_LOCAL void android_touch_uninit(struct android_context* context);
FREERDP_LOCAL void android_touch_channel_connected(struct android_context* context,
                                                   RdpeiClientContext* rdpei);
FREERDP_LOCAL void android_touch_channel_disconnected(struct android_context* context);
FREERDP_LOCAL UINT32 android_touch_get_state(struct android_context* context);
FREERDP_LOCAL BOOL android_touch_submit_frame(freerdp* instance, size_t count, const INT32* ids,
                                              const INT32* actions, const INT32* xs,
                                              const INT32* ys);
FREERDP_LOCAL BOOL android_touch_cancel(freerdp* instance);
FREERDP_LOCAL BOOL android_touch_process_frame(struct android_context* context,
                                               const struct android_event_touch* event);

#endif
