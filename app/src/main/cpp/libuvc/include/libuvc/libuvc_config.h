#ifndef LIBUVC_CONFIG_H
#define LIBUVC_CONFIG_H

/* 我们需要 libuvc 支持 MJPEG */
#define LIBUVC_HAS_JPEG 0

/* 如果你之后想用其自带的转码功能，可以开启这个，但目前我们是直接传 MJPEG */
/* #undef LIBUVC_USE_LIBJPEG */

#endif /* LIBUVC_CONFIG_H */