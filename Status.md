# Blarrg tests

## CPU instrs: OK
01: OK  
02: OK  
03: OK  
04: OK  
05: OK  
06: OK  
07: OK  
08: OK  
09: OK  
10: OK  
11: OK  

## OAM bug: NOK
01: 02  
02: 02  
03: OK  
04: 03  
05: 02  
06: OK  
07: 01  
08: 02  

## Halt bug: OK
IE IF IF DE  
01 10 F1 0C04  
01 00 E1 0C04  
01 01 E1 0411  
11 00 E1 0C04  
11 10 F1 0411  
11 11 F1 0411  
E1 00 E1 0C04  
E1 E0 E1 0C04  
E1 E1 E1 0411  

## Instr timming OK

## Interrupt time NOK - Good if no GBC support
00 00 00  
00 08 0D  
00 00 00  
00 08 0D  
7F8F4AAF  

## Mem timing OK
01: OK  
02: OK  
03: OK  

## DMG sound NOK
01: OK  
02: OK  
03: OK  
04: OK  
05: OK  
06: OK  
07: OK  
08: OK  
09: 01  
10: 01  
11: OK  
12: 01
