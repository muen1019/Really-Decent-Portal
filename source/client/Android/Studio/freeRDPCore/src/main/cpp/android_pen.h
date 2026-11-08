/* Native pen range/contact owner, separate from touch and mouse. MPL-2.0. */
#ifndef FREERDP_CLIENT_ANDROID_PEN_H
#define FREERDP_CLIENT_ANDROID_PEN_H

#include <freerdp/client/rdpei.h>

#define ANDROID_PEN_BEGIN 0
#define ANDROID_PEN_UPDATE 1
#define ANDROID_PEN_END 2
#define ANDROID_PEN_CANCEL 3
#define ANDROID_PEN_HOVER_ENTER 4
#define ANDROID_PEN_HOVER_UPDATE 5
#define ANDROID_PEN_HOVER_EXIT 6
#define ANDROID_PEN_MAX_PENDING 256

typedef struct
{
	BOOL active;
	BOOL touching;
	INT32 x;
	INT32 y;
	INT32 pressure;
	UINT32 penFlags;
} ANDROID_PEN_SLOT;

typedef struct
{
	UINT64 generation;
	size_t pending;
	BOOL cancelRequested;
	ANDROID_PEN_SLOT submitted;
	ANDROID_PEN_SLOT contact;
} ANDROID_PEN_STATE;

struct android_context;
struct android_event_pen;

FREERDP_LOCAL void android_pen_init(struct android_context* context);
/* Called with the existing shared RDPEI-owner touch.lock held. */
FREERDP_LOCAL void android_pen_channel_reset(struct android_context* context);
FREERDP_LOCAL UINT32 android_pen_get_state(struct android_context* context);
FREERDP_LOCAL UINT32 android_pen_get_version(struct android_context* context);
FREERDP_LOCAL BOOL android_pen_submit(freerdp* instance, INT32 action, INT32 x, INT32 y,
                                      INT32 pressure, UINT32 penFlags);
FREERDP_LOCAL BOOL android_pen_cancel(freerdp* instance);
FREERDP_LOCAL BOOL android_pen_process(struct android_context* context,
                                       const struct android_event_pen* event);
#endif
