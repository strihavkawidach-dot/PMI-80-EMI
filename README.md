# PMI-80-EMI
Emulator of PMI-80 for Android.

Nativní Android aplikace v Kotlinu a Jetpack Compose. Vzhled a rozložení kláves jsou inspirované [ASM80 PMI-80](https://www.asm80.com/pmi80.html). Emulace běží lokálně bez internetu a bez WebView.

## Instalace APK

V [GitHub Actions](https://github.com/strihavkawidach-dot/PMI-80-EMI/actions/workflows/android.yml) otevři poslední úspěšný běh **Android debug APK**, stáhni artefakt **app-debug**, rozbal ZIP a nainstaluj `app-debug.apk`. Android může vyžadovat povolení instalace z daného zdroje. Jde o APK podepsané vývojovým klíčem, nikoliv release pro Google Play.

Workflow při každém push do `main`, pull requestu a ručním spuštění provede unit testy, Android lint a sestavení APK. Testy a lint reporty jsou v samostatném artefaktu.

## Lokální sestavení

Vyžaduje JDK 17 a Android SDK (platform 35). Projekt otevři v Android Studiu, nebo nastav `ANDROID_HOME` a spusť:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Ve Windows použij `gradlew.bat`. Přiložený Gradle wrapper stáhne Gradle 8.9. Výsledné APK: `app/build/outputs/apk/debug/app-debug.apk`. Sestavení vyžaduje přístup k repozitářům Google/Maven a distribuci Gradle.

## Emulovaný hardware

- Intel 8080A / Tesla MHB8080A: všechny instrukce včetně původních nedokumentovaných aliasů, příznaky, DAA, T-states, zásobník, HALT, zpožděné EI a přerušení RST 7. Cílový takt 10 MHz / 9; běh je dávkovaný a není přesným časovým simulátorem sběrnice.
- ROM `0000–03FF`: původní 1024B monitor PMI-80. `0400–07FF`: prázdná rozšiřující patice, čte FF. Lze importovat vlastní BIN ROM do 2048 B.
- RAM `1C00–1FFF`, 1024 B; A13 není dekódována, proto zrcadlo `3C00–3FFF`. Nepřipojená paměť čte FF, zápisy do ROM jsou ignorovány.
- Systémová 8255 na F8–FB a uživatelská 8255 na F4–F7. I/O dekódování používá spodní čtyři bity adresy. Implementovány směry portů, režim 0 a bit set/reset. Handshake režimy 1/2 nejsou implementované a zastaví emulaci s chybou.
- Devítimístný multiplexovaný sedmisegmentový displej se skutečnými aktivně nízkými výstupy PA; výběr číslice a skenování matice přes PC. Zhasnutí číslic, které program neobnovuje.
- Původní 25klávesová klávesnice včetně samostatných RE a I. I je maskovatelné přerušení; reaguje pouze po EI. Podržení tlačítka zůstává stiskem do uvolnění.

Kazetový magnetofon není emulován. Klávesy L/S jsou zapojené do původní matice a monitor je rozpozná, ale přenos přes ně nebude fungovat. PC7 je v klidové úrovni 0. Použij import/export BIN. Externí periferie na uživatelském PPI nejsou připojené (vstupy mají pull-up).

## Ovládání

1. Po zapnutí monitor zobrazí `PMI -80`. Stiskni libovolnou klávesu pro vstup do monitoru.
2. Paměť: `M`, adresa `1 C 0 0`, `=`, data `A 5`, `=`. Samotné `=` prochází další adresy.
3. Program: načti BIN (max. 1024 B) od `1C00`, pak `EX 1 C 0 0 =`.
4. Registry: `R`, potom příslušná klávesa (8/SP, 9/HL, A/AF, B/BC, D/DE). BR je příkaz monitoru pro breakpoint.
5. **Pozastavit** zastaví CPU, **Krok** provede jednu instrukci. RE/reset zachová RAM. RAM se ukládá při odchodu z aplikace; vlastní ROM platí pro aktuální proces a lze ji znovu načíst.

## Ověření a zdroje

Unit testy ověřují aritmetiku, všech 10 000 součtů BCD, dekódování všech opcode, přerušení/EI/HALT, zásobník, I/O, PSW, mapování paměti, 8255, sken kláves, boot monitoru a zadávání dat skutečnou klávesnicovou maticí. Nejde o úplné ověření všech kombinací CPU; aplikace není určena jako časově přesný nástroj pro vývoj fyzických periferií.

Zapojení bylo porovnáno s [ovladačem MAME PMI-80](https://github.com/mamedev/mame/blob/master/src/mame/tesla/pmi80.cpp). Monitor je převzat z [maly/fpmi](https://github.com/maly/fpmi), commit `0ab91285b871a2573ebcbb2ccc79e0db3ed36573`, reprezentace `FPGA/PMI.mif`; SHA-1 binárního obrazu `43153441070ed0572f33d2815635eb7bae878e38`. Licence projektu FPMI je zachována v [third_party/FPMI-LICENSE](third_party/FPMI-LICENSE). Implementace Kotlinu je nová. Gradle wrapper pochází z Gradle 8.9 (Apache-2.0; licenční informace jsou v přiloženém JAR a skriptech).
