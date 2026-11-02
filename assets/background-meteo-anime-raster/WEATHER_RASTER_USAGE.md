# SalottoTV — Uso degli sfondi meteo raster

Il pacchetto contiene **120 immagini statiche**, una per ciascuna delle **6 fasce orarie × 20 condizioni meteo**. La cartella del progetto è `assets/background-meteo-raster/`. Ogni WebP è 3840 × 2160, 16:9; si tratta di esportazioni ingrandite dagli originali generati, non di generazione nativa 4K. Le illustrazioni usano campiture opache e cel shading, paesaggi originali, vegetazione rada e una prospettiva aerea obliqua; i soggetti cambiano per ciascuna combinazione.

## Consultazione e nomi

Apri [BACKGROUND_CATALOG.html](BACKGROUND_CATALOG.html) per vedere le immagini con numero e nome sotto ciascuna. I due filtri selezionano fascia oraria e condizione. Cliccando su un'immagine si apre il WebP completo.

I nomi seguono `NNN-weather_<phase>_<weather>_<landscape>_4k.webp`: il numero è stabile e serve a identificare la combinazione; non è una versione. I file sono suddivisi nelle cartelle `day` (001–020), `afternoon` (021–040), `sunset` (041–060), `evening` (061–080), `night` (081–100), `dawn` (101–120). In ciascuna ci sono 20 condizioni.

## Selezione nel launcher

1. Determina la fascia oraria usando ora della località, alba e tramonto. Riutilizza la logica già documentata nella [guida del pacchetto anime](../background-meteo-anime/WEATHER_BACKGROUNDS_USAGE.md): alba da 45 minuti prima a 45 dopo sunrise; tramonto da 60 minuti prima a 20 dopo sunset; sera dai 20 ai 90 minuti dopo sunset; giorno e pomeriggio nel restante intervallo solare, separati al 60%; notte nel tempo restante. Queste soglie sono la convenzione visiva del progetto.
2. Leggi il codice meteo WMO ottenuto dal launcher. Nel [manifest](WEATHER_RASTER_MANIFEST.json), `lookupByPhaseAndWmo[phase][weatherCode]` restituisce il percorso relativo del WebP. Se disponi già del profilo testuale, usa `lookupByPhaseAndWeather[phase][weather]`.
3. Aggiungi al percorso il prefisso `assets/background-meteo-raster/` e carica **una sola immagine**. Per esempio, fase `night` e WMO 65 selezionano `night/092-weather_night_heavy_rain_alpine_gully_pool_4k.webp`. Sole, nuvole, pioggia, neve, gocce sulla lente e fulmini illustrati sono già dentro il file.
4. Mantieni le proporzioni 16:9. Su un display 16:9 l'immagine riempie lo schermo; su altri rapporti applica un ritaglio proporzionale. Al cambio di combinazione puoi usare una breve dissolvenza tra lo sfondo precedente e quello nuovo. Conserva l'ultima condizione valida durante un'assenza temporanea dei dati.

## Profili WMO del pacchetto

| Profilo | Condizione | Codici WMO |
| --- | --- | --- |
| clear_sky | Sereno | 0 |
| mostly_clear | Quasi sereno | 1 |
| partly_cloudy | Parzialmente nuvoloso | 2 |
| overcast | Coperto | 3 |
| fog | Nebbia | 45 |
| freezing_fog | Nebbia gelata | 48 |
| light_drizzle | Pioviggine debole | 51, 56 |
| moderate_drizzle | Pioviggine moderata | 53, 57 |
| heavy_drizzle | Pioviggine intensa | 55 |
| light_rain | Pioggia debole | 61, 66 |
| moderate_rain | Pioggia moderata | 63 |
| heavy_rain | Pioggia forte | 65, 67 |
| light_snow | Neve debole | 71, 77 |
| moderate_snow | Neve moderata | 73 |
| heavy_snow | Neve intensa | 75 |
| light_rain_showers | Rovesci deboli | 80 |
| heavy_rain_showers | Rovesci forti | 81, 82 |
| light_snow_showers | Rovesci deboli di neve | 85 |
| heavy_snow_showers | Rovesci forti di neve | 86 |
| thunderstorm | Temporale | 95, 96, 97, 99 |

Le precipitazioni gelate e la grandine riutilizzano i profili visivi indicati: il set non distingue un'illustrazione dedicata per ogni sottocodice. Per un codice sconosciuto conserva l'ultima combinazione valida anziché attribuire una condizione arbitraria. La chiave del manifest risolve i nomi descrittivi: non costruire il nome del file dal solo codice meteo.

## Immagini statiche e animazioni

Questi WebP sono immagini già composte su **un unico piano**. Puoi applicare una lieve traslazione/zoom dell'intero sfondo, prevedendo margine sufficiente al ritaglio. Per ingresso delle nuvole, colline mobili, parallasse indipendente e fenomeni animati usa i livelli SVG separati in `assets/background-meteo-anime/` e la relativa [specifica matematica](../background-meteo-anime/WEATHER_ANIMATION_MATHEMATICS.md).

La cartella raster definitiva contiene le 120 immagini e i documenti di consultazione/integrazione. Sorgenti generati, prompt e proposte superate restano nella cartella di lavoro esterna al progetto. Le immagini 049–120 sono state autorizzate preventivamente dall'utente, controllate e consegnate senza ulteriori passaggi di approvazione.
