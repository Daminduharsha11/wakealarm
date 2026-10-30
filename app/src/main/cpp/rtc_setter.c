#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <time.h>

// struct rtc_time definition matching Linux kernel
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

// rtc_wkalrm structure matching 0x4028700f (RTC_ALM_SET)
struct rtc_wkalrm {
    unsigned char enabled;
    unsigned char pending;
    unsigned char time[2]; // padding
    struct rtc_time time_data;
};

#define RTC_ALM_SET 0x4028700f

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <epoch_seconds>\n", argv[0]);
        return 1;
    }

    long target_epoch = atol(argv[1]);
    int fd = open("/dev/rtc0", O_RDWR);
    if (fd < 0) {
        perror("Failed to open /dev/rtc0");
        return 1;
    }

    time_t t = (time_t)target_epoch;
    struct tm *g = gmtime(&t);

    struct rtc_wkalrm alm;
    alm.enabled = 1;
    alm.pending = 0;
    alm.time[0] = 0;
    alm.time[1] = 0;

    alm.time_data.tm_sec = g->tm_sec;
    alm.time_data.tm_min = g->tm_min;
    alm.time_data.tm_hour = g->tm_hour;
    alm.time_data.tm_mday = g->tm_mday;
    alm.time_data.tm_mon = g->tm_mon;       // 0-11
    alm.time_data.tm_year = g->tm_year;     // years since 1900
    alm.time_data.tm_wday = 0;
    alm.time_data.tm_yday = 0;
    alm.time_data.tm_isdst = 0;

    if (ioctl(fd, RTC_ALM_SET, &alm) < 0) {
        perror("ioctl RTC_ALM_SET failed");
        close(fd);
        return 1;
    }

    close(fd);
    printf("SUCCESS\n");
    return 0;
}