/*
 * Narrow, bounded HCI ownership probe for the M30 research kernel.
 *
 * This helper deliberately does one operation: bring hci0 up, send the
 * standard HCI Read Local Version command, print the returned event, and
 * bring hci0 down. It does not scan, advertise, connect, inject, or select a
 * target. The caller must stop Android Bluetooth and verify that its SCSC H4
 * descriptor is closed before executing this program.
 *
 * It is freestanding so the resulting AArch64 binary does not depend on an
 * Android NDK or a foreign libc. The build uses only Linux syscall numbers
 * and the Bluetooth socket ABI from the matching kernel headers.
 */

typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned long usize;
typedef long ssize;

#define AF_BLUETOOTH 31
#define SOCK_RAW 3
#define BTPROTO_HCI 1
#define HCI_CHANNEL_RAW 0
#define HCI_COMMAND_PKT 0x01
#define EALREADY 114

/* _IOW('H', 201, int) and _IOW('H', 202, int) on this kernel ABI. */
#define HCIDEVUP 0x400448c9UL
#define HCIDEVDOWN 0x400448caUL

struct sockaddr_hci_local {
    u16 hci_family;
    u16 hci_dev;
    u16 hci_channel;
} __attribute__((packed));

struct pollfd_local {
    int fd;
    short events;
    short revents;
};

#define POLLIN 0x0001

#ifdef PHONE_HCI_PROBE_TEST
static long syscall6(long n, long a0, long a1, long a2, long a3, long a4, long a5);
#else
static long syscall6(long n, long a0, long a1, long a2,
                     long a3, long a4, long a5)
{
    register long x0 asm("x0") = a0;
    register long x1 asm("x1") = a1;
    register long x2 asm("x2") = a2;
    register long x3 asm("x3") = a3;
    register long x4 asm("x4") = a4;
    register long x5 asm("x5") = a5;
    register long x8 asm("x8") = n;
    asm volatile("svc 0"
                 : "+r"(x0)
                 : "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5), "r"(x8)
                 : "memory");
    return x0;
}
#endif

static long sys_read(int fd, void *buf, usize len)
{
    return syscall6(63, fd, (long)buf, (long)len, 0, 0, 0);
}

static long sys_write(int fd, const void *buf, usize len)
{
    return syscall6(64, fd, (long)buf, (long)len, 0, 0, 0);
}

static long sys_close(int fd)
{
    return syscall6(57, fd, 0, 0, 0, 0, 0);
}

static long sys_socket(int domain, int type, int protocol)
{
    return syscall6(198, domain, type, protocol, 0, 0, 0);
}

static long sys_bind(int fd, const void *addr, usize len)
{
    return syscall6(200, fd, (long)addr, (long)len, 0, 0, 0);
}

static long sys_ioctl(int fd, unsigned long request, unsigned long arg)
{
    return syscall6(29, fd, (long)request, (long)arg, 0, 0, 0);
}

static long sys_poll(struct pollfd_local *fds, usize count, int timeout)
{
    struct { long seconds; long nanoseconds; } ts = {
        timeout / 1000, (timeout % 1000) * 1000000L
    };
    return syscall6(73, (long)fds, (long)count, (long)&ts, 0, 0, 0);
}

#ifndef PHONE_HCI_PROBE_TEST
static void sys_exit(int status)
{
    syscall6(93, status, 0, 0, 0, 0, 0);
    for (;;)
        ;
}
#endif

static usize slen(const char *s)
{
    usize n = 0;
    while (s[n])
        ++n;
    return n;
}

static void out(const char *s)
{
    sys_write(1, s, slen(s));
}

static void out_num(long value)
{
    char buf[32];
    usize i = sizeof(buf);
    unsigned long v;
    if (value < 0) {
        out("-");
        v = (unsigned long)(-value);
    } else {
        v = (unsigned long)value;
    }
    if (!v) {
        out("0");
        return;
    }
    while (v && i) {
        buf[--i] = (char)('0' + (v % 10));
        v /= 10;
    }
    sys_write(1, buf + i, sizeof(buf) - i);
}

static void out_hex_byte(u8 v)
{
    static const char digits[] = "0123456789abcdef";
    char b[2] = { digits[v >> 4], digits[v & 0xf] };
    sys_write(1, b, 2);
}

static void out_errno(const char *what, long ret)
{
    out(what);
    out(" ret=");
    out_num(ret);
    out(" errno=");
    out_num(-ret);
    out("\n");
}

static void zero(void *ptr, usize len)
{
    u8 *p = (u8 *)ptr;
    while (len--)
        *p++ = 0;
}

static int probe(void)
{
    int dev = 0;
    /* Nonblocking read after ppoll cannot hang on a spurious readiness event. */
    int fd = (int)sys_socket(AF_BLUETOOTH, SOCK_RAW | 04000, BTPROTO_HCI);
    struct sockaddr_hci_local addr;
    struct pollfd_local pfd;
    u8 command[] = { HCI_COMMAND_PKT, 0x01, 0x10, 0x00 };
    u8 event[260];
    long ret;
    long n = -1;
    int valid = 0;
    struct { unsigned int type_mask; unsigned int event_mask[2]; u16 opcode; } filter;

    if (fd < 0) {
        out_errno("socket", fd);
        return 1;
    }
    zero(&addr, sizeof(addr));
    addr.hci_family = AF_BLUETOOTH;
    addr.hci_dev = (u16)dev;
    addr.hci_channel = HCI_CHANNEL_RAW;
    ret = sys_bind(fd, &addr, sizeof(addr));
    if (ret < 0) {
        out_errno("bind", ret);
        sys_close(fd);
        return 1;
    }
    out("owner_probe=socket_bound hci=hci0\n");
    ret = sys_ioctl(fd, HCIDEVUP, (unsigned long)dev);
    if (ret == -EALREADY) {
        out("owner_probe=controller_already_up refusing_to_take_ownership\n");
        sys_close(fd);
        return 1;
    } else if (ret < 0) {
        out_errno("ioctl(HCIDEVUP)", ret);
        sys_close(fd);
        return 1;
    } else {
        out("owner_probe=controller_up\n");
    }
    /* SOL_HCI=0, HCI_FILTER=2: Command Complete for Read Local Version.
     * Install after bring-up so kernel initialization events are not queued. */
    zero(&filter, sizeof(filter));
    filter.type_mask = 1U << 4;
    filter.event_mask[0] = 1U << 14;
    filter.opcode = 0x1001;
    ret = syscall6(208, fd, 0, 2, (long)&filter, sizeof(filter), 0);
    if (ret < 0) {
        out_errno("setsockopt(HCI_FILTER)", ret);
        goto down;
    }
    n = sys_write(fd, command, sizeof(command));
    if (n != (long)sizeof(command)) {
        out_errno("write(Read Local Version)", n);
        goto down;
    }
    out("hci_command=read_local_version opcode=0x1001 bytes=4\n");
    pfd.fd = fd;
    pfd.events = POLLIN;
    pfd.revents = 0;
    ret = sys_poll(&pfd, 1, 3000);
    if (ret <= 0 || !(pfd.revents & POLLIN)) {
        out_errno("poll(HCI event)", ret);
        goto down;
    }
    n = sys_read(fd, event, sizeof(event));
    if (n < 0) {
        out_errno("read(HCI event)", n);
        goto down;
    }
    out("hci_event_bytes=");
    out_num(n);
    out("\nhci_event_hex=");
    for (long i = 0; i < n; ++i) {
        out_hex_byte(event[i]);
        if (i + 1 != n)
            out(" ");
    }
    out("\n");
    /* packet type, event, payload length, credits, opcode LE, status,
     * then the eight-byte Read Local Version return value. */
    valid = n == 15 && event[0] == 4 && event[1] == 14 &&
        event[2] == 12 && event[4] == 1 && event[5] == 0x10 && event[6] == 0;
    out(valid ? "result=validated_version_response\n" : "result=invalid_response\n");

down:
    ret = sys_ioctl(fd, HCIDEVDOWN, (unsigned long)dev);
    if (ret < 0)
        out_errno("ioctl(HCIDEVDOWN)", ret);
    else
        out("owner_probe=controller_down\n");
    sys_close(fd);
    return (valid && ret >= 0) ? 0 : 1;
}

#ifndef PHONE_HCI_PROBE_TEST
void _start(void)
{
    sys_exit(probe());
}
#endif
