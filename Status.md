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

## Halt bug: NOK
IE IF IF DE  
01 10 11 0C04  
01 00 01 0C04  
01 01 01 0C04  
11 00 01 0C04  
11 10 11 0C04  
11 11 11 0C04  
E1 00 01 0C04  
E1 E0 E1 0C04  
E1 E1 E1 0C04  
3DB103C3

## Instr timming NOK

## Interrupt time NOK
01 00 FC  
01 08 0D  
01 00 FC  
00 08 0D  
FECD042E  

## Meme timing NOK
01: 01  
02: 01  
03: 01  

## DMG sound NOK
01: OK  
02: 02  
03: 02  
04: 02  
05: 02  
06: 01  
07: 05  
08: 01  
09: 01  
10: 01  
11: 03  
12: Crash
