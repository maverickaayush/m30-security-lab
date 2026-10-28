#define LAB_TEST
#include "../host/phone_linux_lab.c"
#include <assert.h>
int main(void) {
    uint8_t ad[18] = {17,7}; memcpy(ad+2, lab_uuid, 16);
    assert(lab_ad(ad,18)); assert(!lab_ad(ad,17));
    ad[1]=9; assert(!lab_ad(ad,18)); ad[1]=7;
    uint8_t p[33]={4,0x3e,30,2,1,0,0,0,0,0,0,0,0,18};
    memcpy(p+14,ad,18); p[32]=200;
    assert(parse_report(p,33,0)==1);
    for (size_t i=0;i<33;i++) assert(parse_report(p,i,0)<=0);
    p[4]=2; assert(parse_report(p,33,0)==-1); p[4]=1;
    p[13]=31; assert(parse_report(p,33,0)==-1); p[13]=18;
    p[15]=9; assert(parse_report(p,33,0)==0);
    assert(valid_id("m30-20261028_1")); assert(!valid_id("bad\"id")); assert(!valid_id(""));
    puts("PASS: AD UUID matching, 33 truncations, multi-report bounds, malformed lengths, unrelated AD, experiment IDs");
    return 0;
}
