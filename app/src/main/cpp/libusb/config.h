#ifndef CONFIG_H
#define CONFIG_H

/* 基础平台定义 */
#define HAVE_CLOCK_GETTIME 1
#define OS_LINUX 1
#define PLATFORM_POSIX 1
#define HAVE_TIMERFD 1
#define HAVE_EVENTFD 1
#define HAVE_SYS_TIME_H 1
#define HAVE_NFDS_T 1
#define THREADS_POSIX 1
#define PACKAGE_VERSION "1.0.26"
#define POLL_NFDS_TYPE nfds_t
#define HAVE_LINUX_NETLINK 1

/* 修复 PRINTF_FORMAT 报错的关键点 */
#if defined(__GNUC__)
#define PRINTF_FORMAT(a, b) __attribute__ ((format (printf, a, b)))
#else
#define PRINTF_FORMAT(a, b)
#endif

/* 顺便定义这个，防止之后报错 */
#define DEFAULT_VISIBILITY __attribute__((visibility("default")))

#endif /* CONFIG_H */