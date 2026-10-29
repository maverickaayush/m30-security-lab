/* Phone-local, supervised Linux HCI lab. No daemon or arbitrary command API.
 * Build against pinned BlueZ ABI headers; no libbluetooth linkage required.
 * The caller must verify Android HAL stopped and H4 fd released first. */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/file.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/l2cap.h>
#include <bluetooth/rfcomm.h>

static volatile sig_atomic_t cancelled;
static const char *run_id;
static unsigned reports;
static void stop(int sig) { cancelled = sig; }
static long long millis(clockid_t clock) {
    struct timespec t;
    if (clock_gettime(clock, &t)) return -1;
    return (long long)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}
static void event(const char *name, long value) {
    printf("{\"run_id\":\"%s\",\"event\":\"%s\",\"value\":%ld,\"utc_ms\":%lld,\"mono_ms\":%lld}\n",
           run_id, name, value, millis(CLOCK_REALTIME), millis(CLOCK_MONOTONIC));
}
/* UUID bytes in the BLE AD little-endian order. No unrelated payload logged. */
static const uint8_t lab_uuid[16] = {1,0x3b,3,0xa7,0xe7,0x7d,0xe7,0x9f,0x15,0x4c,0x59,0x46,0xd6,0x42,0x5f,0x7f};
static int lab_ad(const uint8_t *p, size_t size) {
    for (size_t i = 0; i < size;) {
        size_t n = p[i++];
        if (!n) break;
        if (n > size - i) return 0;
        if ((p[i] == 6 || p[i] == 7) && (n - 1) % 16 == 0)
            for (size_t j = 1; j < n; j += 16)
                if (!memcmp(p + i + j, lab_uuid, 16)) return 1;
        i += n;
    }
    return 0;
}
static int parse_report(const uint8_t *p, size_t n, int log_it) {
    if (n < 5 || p[0] != 4 || p[1] != 0x3e || (size_t)p[2] + 3 != n || p[3] != 2) return 0;
    size_t pos = 5;
    int matches = 0;
    for (unsigned k = 0; k < p[4]; k++) {
        if (n - pos < 10) return -1;
        size_t len = p[pos + 8];
        if (len > n - pos - 10) return -1;
        if (lab_ad(p + pos + 9, len)) {
            matches++;
            if (log_it) event("lab_advertisement_rssi", (int8_t)p[pos + 9 + len]);
        }
        pos += 10 + len;
    }
    return pos == n ? matches : -1;
}
static int receive_event(int fd, uint8_t *buf, size_t cap, long long deadline, int cleanup) {
    while ((cleanup || !cancelled) && millis(CLOCK_MONOTONIC) < deadline) {
        int remaining = (int)(deadline - millis(CLOCK_MONOTONIC));
        struct pollfd p = {fd, POLLIN, 0};
        int r = poll(&p, 1, remaining < 200 ? remaining : 200);
        if (r < 0 && errno == EINTR) continue;
        if (r < 0 || (p.revents & (POLLERR | POLLHUP | POLLNVAL))) return -1;
        if (!r) continue;
        ssize_t n = read(fd, buf, cap);
        if (n < 0 && (errno == EAGAIN || errno == EINTR)) continue;
        return (int)n;
    }
    return 0;
}
static int command(int fd, uint16_t opcode, const uint8_t *args, size_t len, int cleanup) {
    uint8_t packet[260] = {1, opcode & 255, opcode >> 8, (uint8_t)len};
    if (len > 255) return -1;
    if (len) memcpy(packet + 4, args, len);
    if (write(fd, packet, len + 4) != (ssize_t)(len + 4)) return -1;
    event("command_sent", opcode);
    long long deadline = millis(CLOCK_MONOTONIC) + 2000;
    while (1) {
        int n = receive_event(fd, packet, sizeof(packet), deadline, cleanup);
        if (n <= 0) { event("command_timeout_or_cancel", opcode); return -1; }
        if (n >= 7 && packet[0] == 4 && packet[1] == 14 && packet[2] + 3 == n &&
            packet[4] == (opcode & 255) && packet[5] == (opcode >> 8)) {
            event("command_status", packet[6]);
            if (opcode == 0x1001 && (n != 15 || packet[6])) return -1;
            if (opcode == 0x1001) event("manufacturer", packet[11] | (packet[12] << 8));
            return packet[6] ? -1 : 0;
        }
        if (n >= 7 && packet[0] == 4 && packet[1] == 15 &&
            packet[5] == (opcode & 255) && packet[6] == (opcode >> 8) && packet[3]) {
            event("command_rejected", packet[3]); return -1;
        }
    }
}
static int valid_id(const char *s) {
    size_t n = strlen(s);
    if (!n || n > 64) return 0;
    for (size_t i = 0; i < n; i++)
        if (!(s[i] >= 'a' && s[i] <= 'z') && !(s[i] >= 'A' && s[i] <= 'Z') &&
            !(s[i] >= '0' && s[i] <= '9') && s[i] != '-' && s[i] != '_') return 0;
    return 1;
}
static int parse_address(const char *s, bdaddr_t *out) {
    if (strlen(s) != 17) return -1;
    for (int i=0;i<6;i++) {
        char octet[3] = {s[i*3],s[i*3+1],0}, *end;
        for (int j=0;j<2;j++) if (!((octet[j]>='0' && octet[j]<='9') ||
            (octet[j]>='a' && octet[j]<='f') || (octet[j]>='A' && octet[j]<='F'))) return -1;
        if (i<5 && s[i*3+2]!=':') return -1;
        out->b[5-i]=(uint8_t)strtoul(octet,&end,16);
        if (*end) return -1;
    }
    return 0;
}
static int wait_socket(int fd, short events, long long deadline) {
    while (!cancelled && millis(CLOCK_MONOTONIC)<deadline) {
        struct pollfd p={fd,events,0};
        int r=poll(&p,1,100);
        if (r<0 && errno==EINTR) continue;
        if (r<0) return -1;
        if (r) return (p.revents & events) ? 0 : -1;
    }
    errno=ETIMEDOUT; return -1;
}
static int classic_request(const char *mode, const bdaddr_t *local, const bdaddr_t *peer,
                           int channel, long long deadline) {
    int rfcomm=!strcmp(mode,"rfcomm");
    int fd=socket(AF_BLUETOOTH,(rfcomm?SOCK_STREAM:SOCK_RAW)|SOCK_NONBLOCK|SOCK_CLOEXEC,
                  rfcomm?BTPROTO_RFCOMM:BTPROTO_L2CAP);
    if(fd<0){event("protocol_socket_failed",errno);return 1;}
    int r, result=1;
    long long start=millis(CLOCK_MONOTONIC);
    if(rfcomm){
        struct sockaddr_rc addr={.rc_family=AF_BLUETOOTH,.rc_bdaddr=*local,.rc_channel=0};
        r=bind(fd,(void *)&addr,sizeof(addr));
        if(r) goto failed;
        addr.rc_bdaddr=*peer; addr.rc_channel=channel;
        r=connect(fd,(void *)&addr,sizeof(addr));
    }else{
        struct sockaddr_l2 addr={.l2_family=AF_BLUETOOTH,.l2_bdaddr=*local};
        r=bind(fd,(void *)&addr,sizeof(addr));
        if(r) goto failed;
        addr.l2_bdaddr=*peer;
        r=connect(fd,(void *)&addr,sizeof(addr));
    }
    if(r && errno!=EINPROGRESS) goto failed;
    if(r && wait_socket(fd,POLLOUT,deadline)) goto failed;
    int error=0; socklen_t size=sizeof(error);
    if(getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)) goto failed;
    if(error){errno=error;goto failed;}
    event("protocol_connected_ms",millis(CLOCK_MONOTONIC)-start);
    const uint8_t ping[]="M30LAB/1 PING\n", pong[]="M30LAB/1 PONG\n";
    uint8_t request[20]={8,0xa1,16,0,'M','3','0','L','A','B','-','E','C','H','O','-','0','0','0','1'};
    const uint8_t *tx=rfcomm?ping:request;
    size_t len=rfcomm?sizeof(ping)-1:sizeof(request), sent=0;
    while(sent<len){
        if(wait_socket(fd,POLLOUT,deadline)) goto failed;
        ssize_t n=send(fd,tx+sent,len-sent,MSG_NOSIGNAL);
        if(n<0 && (errno==EAGAIN||errno==EINTR)) continue;
        if(n<=0) goto failed;
        sent+=n;
    }
    event("protocol_request_bytes",sent);
    uint8_t response[64]; size_t received=0;
    while(received<sizeof(response)){
        if(wait_socket(fd,POLLIN,deadline)) goto failed;
        ssize_t n=recv(fd,response+received,sizeof(response)-received,0);
        if(n<0 && (errno==EAGAIN||errno==EINTR)) continue;
        if(n<=0) goto failed;
        received+=n;
        if(rfcomm){
            if(received>=sizeof(pong)-1){result=(received==sizeof(pong)-1&&!memcmp(response,pong,received))?0:1;break;}
        }else{
            if(received>=4 && response[1]==0xa1){
                result=received==20 && response[0]==9 && response[2]==16 && response[3]==0 &&
                    !memcmp(response+4,request+4,16)?0:1;
                if(response[0]==1) event("l2cap_echo_rejected",1);
                break;
            }
            received=0;
        }
    }
    event(result?"protocol_invalid_response":"protocol_response_verified",received);
    goto done;
failed:
    event("protocol_error",errno);
done:
    close(fd); event("protocol_socket_closed_ms",millis(CLOCK_MONOTONIC)-start);
    return result;
}
#ifndef LAB_TEST
int main(int argc, char **argv) {
    if ((argc != 5 && argc != 7) || strcmp(argv[1], "hci0") || !valid_id(argv[3])) {
        fprintf(stderr, "usage: phone_linux_lab hci0 inspect|scan|down|echo|echo-seq|rfcomm RUN_ID SECONDS [TARGET CHANNEL_OR_COUNT]; echo-seq allows 1..60 seconds and 1..100 requests at least 600 ms apart\n"); return 2;
    }
    char *end;
    long seconds = strtol(argv[4], &end, 10);
    if (*end || seconds < 1 || seconds > 60 ||
        (strcmp(argv[2], "inspect") && strcmp(argv[2], "scan") && strcmp(argv[2], "down") && strcmp(argv[2],"rfcomm") && strcmp(argv[2],"echo") && strcmp(argv[2],"echo-seq"))) return 2;
    int sequence=!strcmp(argv[2],"echo-seq");
    if(!sequence && seconds>20) return 2;
    int classic=!strcmp(argv[2],"rfcomm") || !strcmp(argv[2],"echo") || sequence;
    bdaddr_t target; long channel=0;
    if(classic){
        if(argc!=7 || parse_address(argv[5],&target)) return 2;
        channel=strtol(argv[6],&end,10);
        if(*end || channel<1 || channel>(sequence ? 100 : 30)) return 2;
        if(sequence && seconds*1000/channel<600) return 2;
    }else if(argc!=5) return 2;
    run_id = argv[3];
    setvbuf(stdout, NULL, _IOLBF, 0);
    struct sigaction sa = {.sa_handler = stop};
    sigemptyset(&sa.sa_mask);
    sigaction(SIGINT, &sa, NULL); sigaction(SIGTERM, &sa, NULL); sigaction(SIGALRM, &sa, NULL);
    int lock = open("/data/local/tmp/m30-linux-lab.lock", O_CREAT | O_RDWR | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB)) { event("lock_failed", errno); return 1; }
    int fd = socket(AF_BLUETOOTH, SOCK_RAW | SOCK_NONBLOCK | SOCK_CLOEXEC, BTPROTO_HCI);
    if (fd < 0) { event("socket_failed", errno); return 1; }
    struct sockaddr_hci addr = {.hci_family=AF_BLUETOOTH,.hci_dev=0,.hci_channel=HCI_CHANNEL_RAW};
    if (bind(fd, (void *)&addr, sizeof(addr))) { event("bind_failed", errno); close(fd); return 1; }
    struct hci_dev_info info = {.dev_id=0};
    if (ioctl(fd, HCIGETDEVINFO, &info)) { event("info_failed", errno); close(fd); return 1; }
    event("controller_up_before", !!(info.flags & (1U << HCI_UP)));
    if (!strcmp(argv[2], "inspect")) { close(fd); return 0; }
    if (!strcmp(argv[2], "down")) {
        int r = 0;
        if (info.flags & (1U << HCI_UP)) r = ioctl(fd, HCIDEVDOWN, 0UL);
        event("recovery_down", r ? errno : 0); close(fd); return r != 0;
    }
    if (info.flags & (1U << HCI_UP)) { event("refuse_existing_owner", 1); close(fd); return 1; }
    alarm(sequence ? 65 : 30);
    if (ioctl(fd, HCIDEVUP, 0UL)) { event("up_failed", errno); close(fd); return 1; }
    event("linux_acquired", 0);
    struct hci_filter filter = {.type_mask=1U<<4,.event_mask={1U<<14 | 1U<<15,1U<<(0x3e - 32)},.opcode=0};
    int result = 1, scanning = 0;
    if (setsockopt(fd, SOL_HCI, HCI_FILTER, &filter, sizeof(filter))) goto cleanup;
    if (command(fd, 0x1001, NULL, 0, 0)) goto cleanup;
    if(classic){
        if(ioctl(fd,HCIGETDEVINFO,&info)) goto cleanup;
        if(sequence){
            long long sequence_start=millis(CLOCK_MONOTONIC);
            long long run_deadline=sequence_start+seconds*1000;
            long long interval=seconds*1000/channel;
            int passed=0, failed=0, consecutive_failures=0;
            event("sequence_limit",channel);
            for(int i=0;i<channel && !cancelled && millis(CLOCK_MONOTONIC)<run_deadline;i++){
                long long scheduled=sequence_start+i*interval;
                while(!cancelled && millis(CLOCK_MONOTONIC)<scheduled)
                    poll(NULL,0,100);
                if(cancelled || millis(CLOCK_MONOTONIC)>=run_deadline) break;
                long long op_deadline=millis(CLOCK_MONOTONIC)+5000;
                if(op_deadline>run_deadline) op_deadline=run_deadline;
                event("request_index",i+1);
                if(classic_request("echo",&info.bdaddr,&target,1,op_deadline)){
                    failed++;
                    event("request_failed",i+1);
                    consecutive_failures++;
                    if(consecutive_failures>=3){
                        event("abort_after_consecutive_failures",consecutive_failures);
                        break;
                    }
                    continue;
                }
                passed++;
                consecutive_failures=0;
                event("request_passed",i+1);
            }
            event("requests_passed",passed);
            event("requests_failed",failed);
            result=cancelled?3:(failed?1:(passed==channel?0:4));
        }else{
            result=classic_request(argv[2],&info.bdaddr,&target,(int)channel,
                                   millis(CLOCK_MONOTONIC)+5000);
        }
        goto cleanup;
    }
    /* Passive scan; 60 ms interval / 30 ms window; no scan requests. */
    const uint8_t params[] = {0,0x60,0,0x30,0,0,0};
    if (command(fd, 0x200b, params, sizeof(params), 0)) goto cleanup;
    const uint8_t enable[] = {1,0};
    scanning = 1; /* Disable even if enable's completion is lost. */
    if (command(fd, 0x200c, enable, sizeof(enable), 0)) goto cleanup;
    event("scan_started", seconds);
    long long deadline = millis(CLOCK_MONOTONIC) + seconds * 1000;
    while (!cancelled && millis(CLOCK_MONOTONIC) < deadline) {
        uint8_t packet[260];
        int n = receive_event(fd, packet, sizeof(packet), deadline, 0);
        if (n < 0) goto cleanup;
        if (n) {
            int matches = parse_report(packet, n, 1);
            if (matches < 0) event("malformed_advertising_report", 1);
            else reports += matches;
        }
    }
    event(cancelled ? "cancelled" : "scan_deadline", cancelled);
    result = cancelled ? 3 : (reports ? 0 : 4);
cleanup:
    if (scanning) {
        const uint8_t disable[] = {0,0};
        if (command(fd, 0x200c, disable, sizeof(disable), 1)) result = 1;
        else event("scan_disabled", 0);
    }
    if (ioctl(fd, HCIDEVDOWN, 0UL)) { event("down_failed", errno); result = 1; }
    else event("linux_released", 0);
    event("lab_reports", reports);
    close(fd); close(lock);
    return result;
}
#endif
