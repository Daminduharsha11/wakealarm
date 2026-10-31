#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <time.h>

// struct rtc_time, matching the Linux kernel
struct rtc_time {
    int tm_sec;
    int tm_min;
    int tm_hour;
    int tm_mday;
    int tm_mon;
    int tm_year;
    int tm_wday;
    int tm_yday;
    int tm_isdst;
};

// struct rtc_wkalrm (40 bytes: 2 bytes + 2 padding + 36)
struct rtc_wkalrm {
    unsigned char enabled;
    unsigned char pending;
    unsigned char pad[2];
    struct rtc_time time;
};

#define RTC_RD_TIME   0x80247009  /* _IOR('p', 0x09, struct rtc_time)   */
#define RTC_WKALM_SET 0x4028700f  /* _IOW('p', 0x0f, struct rtc_wkalrm) */
#define RTC_WKALM_RD  0x80287010  /* _IOR('p', 0x10, struct rtc_wkalrm) */

static long rtc_to_epoch(const struct rtc_time *r) {
    struct tm t;
    memset(&t, 0, sizeof(t));
    t.tm_sec = r->tm_sec;
    t.tm_min = r->tm_min;
    t.tm_hour = r->tm_hour;
    t.tm_mday = r->tm_mday;
    t.tm_mon = r->tm_mon;
    t.tm_year = r->tm_year;
    return (long)timegm(&t);
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <delta_seconds>   (alarm = RTC now + delta)\n", argv[0]);
        fprintf(stderr, "       %s 0               (disable alarm)\n", argv[0]);
        return 1;
    }

    long delta = atol(argv[1]);

    int fd = open("/dev/rtc0", O_RDWR);
    if (fd < 0) {
        perror("open /dev/rtc0");
        return 1;
    }

    struct rtc_wkalrm alm;
    memset(&alm, 0, sizeof(alm));

    // Disable request
    if (delta <= 0) {
        alm.enabled = 0;
        if (ioctl(fd, RTC_WKALM_SET, &alm) < 0) {
            perror("ioctl RTC_WKALM_SET (disable)");
            close(fd);
            return 1;
        }
        close(fd);
        printf("SUCCESS disabled\n");
        return 0;
    }

    // Read the RTC's OWN time (the wall clock may be wildly different, e.g. RTC in 1971)
    struct rtc_time now;
    memset(&now, 0, sizeof(now));
    if (ioctl(fd, RTC_RD_TIME, &now) < 0) {
        perror("ioctl RTC_RD_TIME");
        close(fd);
        return 1;
    }

    long rtc_now = rtc_to_epoch(&now);
    time_t target = (time_t)(rtc_now + delta);
    struct tm *g = gmtime(&target);
    if (!g) {
        fprintf(stderr, "gmtime failed\n");
        close(fd);
        return 1;
    }

    alm.enabled = 1;
    alm.pending = 0;
    alm.time.tm_sec = g->tm_sec;
    alm.time.tm_min = g->tm_min;
    alm.time.tm_hour = g->tm_hour;
    alm.time.tm_mday = g->tm_mday;
    alm.time.tm_mon = g->tm_mon;     // 0-11
    alm.time.tm_year = g->tm_year;   // years since 1900

    if (ioctl(fd, RTC_WKALM_SET, &alm) < 0) {
        perror("ioctl RTC_WKALM_SET");
        close(fd);
        return 1;
    }

    // Read back to verify it really is armed
    struct rtc_wkalrm chk;
    memset(&chk, 0, sizeof(chk));
    if (ioctl(fd, RTC_WKALM_RD, &chk) < 0) {
        perror("ioctl RTC_WKALM_RD");
        close(fd);
        return 1;
    }
    close(fd);

    if (!chk.enabled) {
        fprintf(stderr, "alarm readback: NOT enabled\n");
        return 1;
    }

    printf("SUCCESS rtc_now=%ld alarm=%ld delta=%ld readback=%04d-%02d-%02d %02d:%02d:%02d\n",
           rtc_now, (long)target, delta,
           chk.time.tm_year + 1900, chk.time.tm_mon + 1, chk.time.tm_mday,
           chk.time.tm_hour, chk.time.tm_min, chk.time.tm_sec);
    return 0;
}