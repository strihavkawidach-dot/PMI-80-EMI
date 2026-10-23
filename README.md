# PMI-80 EMU
Emulator of PMI-80 for Android.

Nativní Android aplikace v Kotlinu a Jetpack Compose. Panel verze 1.1 vychází z fotografií původního PMI-80: olivová deska v krémovém a žlutém krytu, červený průsvitný kryt s devíti čočkami a samostatný světlý štítek klávesnice. Modré funkční klávesy a tmavé hexadecimální klávesy mají stejné rozměry; popisky registrů jsou nad tlačítky. Rozložení odpovídá také [ASM80 PMI-80](https://www.asm80.com/pmi80.html). Emulace běží lokálně bez internetu a bez WebView.

## Instalace APK

V [GitHub Actions](https://github.com/strihavkawidach-dot/PMI-80-EMI/actions/workflows/android.yml) otevři poslední úspěšný běh **Android debug APK**, stáhni artefakt **PMI-80-EMU-debug**, rozbal ZIP a nainstaluj `app-debug.apk`. Android může vyžadovat povolení instalace z daného zdroje. Jde o APK podepsané vývojovým klíčem, nikoliv release pro Google Play.

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

## Verze 1.2 – krabička, zdroj a původní tabulka

Aplikace začíná zavřenou žlutou krabičkou. **Otevřít krabičku** animuje víko; pak přepínačem **SÍŤ** zapneš zdroj SN 080. Zelená kontrolka sítě a červené kontrolky +5 V, −5 V a +12 V vycházejí z dodaných videí. CPU se spustí až po dokončení vizuální sekvence napětí, od reset vektoru skutečného monitoru. Vypnutí okamžitě zastaví CPU a zhasne displej; kontrolky napětí postupně doběhnou. Jde o stylizované časování, nikoli simulaci napájecích obvodů. Sekvence se na pozadí pozastavuje. RAM zůstává zachována i při vypnutí. Při otočení telefonu se stav krabičky a zdroje uchová ve ViewModelu; nové spuštění procesu začíná zavřenou krabičkou.

**Návod → Originální tabulka** a tlačítko nad deskou otevřou dodanou fotografii štítku z víka (instrukce 8080 a blokové schéma). Je přiložená přímo v aplikaci, funguje offline a pro čtení se otočí. Podporuje přiblížení až 6×, posouvání i tlačítka zvětšení. Původní JPEG je zachován bez úprav; čitelnost drobného tisku je omezená dodaným rozlišením. Textový návod k obsluze monitoru zůstává dostupný vedle fotografie. Podklady poskytl vlastník tohoto projektu.

## Verze 1.2.1 – originální víko a název EMU

Úvodní víko nyní používá dodanou fotografii skutečné krabičky: původní znak Tesla, text ŠKOLSKÝ MIKROPOČÍTAČ / PMI 80 a TESLA PIEŠŤANY. Aplikace zobrazuje výřez originálu a zachovává autentický povrch i patinu; původní JPEG není upravený. Otevírání víka funguje stejně jako dříve. Název aplikace v Androidu, úvodní obrazovka, název projektu Gradle i APK artefaktu nyní používají EMU. Adresa existujícího GitHub repozitáře zůstává stejná.

## Verze 1.3 – fotografická deska a displej

Kreslené součástky a klávesy nahrazuje skutečný nerozsvícený záběr desky z dodaného videa `VID_20261023_131232.mp4`. Původní snímek je uložen bez retuše; při zobrazení se otočí a ořízne na sekci displeje a klávesnice. Stejný fotografický podklad se odhaluje pod otevíraným víkem. Nad původními klávesami jsou průhledné dotykové plochy s odezvou stisku a podporou přístupnosti. Nad původním krytem displeje se vykreslují pouze aktivní segmenty. Zhasnuté segmenty, umělé čočky a desetinné tečky se nevykreslují; CPU a původní monitor nejsou upraveny. Displej lze klepnutím nebo tlačítkem zvětšit. Víko bylo následně dokončeno podle nové fotografie ve verzi 1.3.1.

## Verze 1.3.1 – finální fotografie víka

Úvod používá novou čelní fotografii víka s kompletním původním označením TESLA PIEŠŤANY k. p. Ořez při zobrazení odstraní okolní stůl a zdroj a zachová znak Tesla i oba hlavní nápisy. Poměr stran se vypočítává ze zdrojového výřezu, takže fotografie není roztažená. Původní JPEG se nemění a nápisy nejsou dodatečně překreslené. Fotografická deska, dotyková klávesnice, sedmisegmentový displej a sekvence zdroje zůstávají stejné jako ve verzi 1.3.0.

## Verze 1.4 – detail displeje podle dodané fotografie

Detail displeje používá fotografický podklad vytvořený z dodané detailní fotografie. Podklad byl retušován pomocí generativního obrazového nástroje: byly odstraněny rozsvícené znaky a jejich světlo, aby fotografie nezobrazovala pevný nápis při vypnutí nebo při běhu jiného programu. Jde tedy o retušovaný fotografický podklad, ne nezměněný originál. Nad jednotlivými čočkami se kreslí pouze aktivní segmenty ze skutečného monitoru a uživatelského programu, v měřítku podle fotografie. Bez desetinných teček nebo viditelných neaktivních segmentů. Stejná geometrie se používá na desce i ve zvětšeném detailu, s jemnou růžovočervenou září. Originální soubory uživatele se nemění.

Úvodní víko se nyní zobrazuje z celé nové fotografie, bez vnitřního ořezu a bez deformace; jeho rozměry na obrazovce vycházejí přímo z poměru stran fotografie.

## Verze 1.4.1 – kalibrace podle čelní fotografie

Základem displeje je nyní čtvrtá z doplněných čelních fotografií (`1000044652.jpg`), retušovaná do zhasnutého stavu. Výřez obsahuje celý kryt včetně prázdných okrajů. Devět pozic se určuje samostatnými měřenými souřadnicemi středů čoček, nikoli rovnoměrnou mřížkou. Upravené měřítko zachovává polohu displeje vůči desce i mírnou perspektivu. Záře odpovídá velikosti jednotlivých čoček. Krajní dvojice krátkých svislých segmentů při uvítání zůstávají zachované: jsou součástí původního monitoru a jsou vidět na referenčních fotografiích. Desetinné tečky se nepřidávají. Změny jsou pouze ve vykreslování, paměť ani CPU se nemění.

## Verze 1.5 – fotografický panel SN 080

Napáječ používá fotografii skutečného panelu SN 080 místo kresleného panelu. Zapnutý ustálený stav je původní dodaná fotografie s rozsvícenými kontrolkami, skutečnou páčkou, nápisy a znakem. Vypnutý podklad je retušovaná varianta fotografie vytvořená obrazovým nástrojem: kontrolky jsou zhasnuté a páčka je v poloze O. Při přechodech se jednotlivé fotografované kontrolky a poloha přepínače prolínají podle stávající sekvence zdroje. Originální fotografie uživatele se nemění.

Přepínač má přímo v panelu dotykovou plochu 48 dp s rolí přepínače pro přístupnost; pod panelem zůstává velké textové tlačítko. Celý panel se zobrazuje v poměru stran podle fotografie. Ořez při zobrazení vynechává okolní pracovní stůl, ne nápisy na čelním panelu. Pořadí napětí a pravidla spouštění CPU zůstávají stejné.
