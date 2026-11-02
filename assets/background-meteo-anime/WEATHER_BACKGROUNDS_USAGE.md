# SalottoTV — Guida agli sfondi meteo vettoriali

Il pacchetto contiene **139 asset SVG utilizzabili: 19 livelli base e 120 sfondi completi**, ottenuti combinando 6 fasce orarie con 20 condizioni meteo. Include inoltre `weather_scene_examples.svg`, una tavola con quattro esempi di composizione, per un totale di 140 file SVG. L’elenco navigabile dei 120 sfondi si trova in `WEATHER_BACKGROUND_INDEX.md`. La specifica per ingresso, parallasse continuo e fenomeni di tutte le 120 combinazioni si trova in [WEATHER_ANIMATION_MATHEMATICS.md](WEATHER_ANIMATION_MATHEMATICS.md). Tutti gli asset sono vettoriali: tracciati, sfumature e trasparenze, senza bitmap incorporate, font esterni o risorse di rete. I file sono statici: orario, meteo, movimento e lampeggi vengono gestiti dal launcher.

Ogni asset ha `width="3840"`, `height="3840"` e `viewBox="0 0 3840 3840"`. Si può renderizzare anche a risoluzioni superiori. Lo stile usa silhouette e ombre a campiture, ispirate alle illustrazioni di animazione giapponese; la resa è volutamente vettoriale.

## Posizione nel progetto

Tutto il pacchetto è raccolto in `SalottoTV/assets/background-meteo-anime/`. La root del progetto contiene soltanto la directory `assets/`. All'interno del pacchetto:

- `layers/`: i 19 livelli SVG separati;
- `backgrounds/`: le 120 combinazioni SVG, suddivise per fascia oraria;
- `WEATHER_BACKGROUNDS_USAGE.md`: questa guida;
- `WEATHER_BACKGROUND_INDEX.md`: indice navigabile delle combinazioni;
- `WEATHER_ANIMATION_MATHEMATICS.md`: formule e parametri per animare tutte le combinazioni;
- `weather_scene_examples.svg`: tavola di esempi.

I percorsi relativi dei documenti partono sempre dalla cartella `background-meteo-anime/`. Nel codice del launcher il prefisso di caricamento dei file è quindi `assets/background-meteo-anime/`, seguito dal percorso relativo nell'indice. La sottocartella `layers/` contiene i livelli base separati.

## Sfondi completi: selezione rapida

La cartella `backgrounds/` contiene sei sottocartelle: `dawn`, `day`, `afternoon`, `sunset`, `evening`, `night`. Ognuna contiene 20 SVG, nominati con lo schema `weather_<time>_<condition>.svg`. Ad esempio:

- `backgrounds/day/weather_day_clear_sky.svg`
- `backgrounds/sunset/weather_sunset_partly_cloudy.svg`
- `backgrounds/night/weather_night_heavy_rain.svg`
- `backgrounds/day/weather_day_thunderstorm.svg`

Le 20 condizioni sono: `clear_sky`, `mostly_clear`, `partly_cloudy`, `overcast`, `fog`, `freezing_fog`, `light_drizzle`, `moderate_drizzle`, `heavy_drizzle`, `light_rain`, `moderate_rain`, `heavy_rain`, `light_snow`, `moderate_snow`, `heavy_snow`, `light_rain_showers`, `heavy_rain_showers`, `light_snow_showers`, `heavy_snow_showers`, `thunderstorm`.

Consulta la colonna WMO nell'indice per scegliere il nome della condizione. Questi 120 file non sono copie rinominate: le fasce orarie cambiano cielo, astri e tinta delle nuvole; le condizioni cambiano copertura, attenuazione della luce, densità e lunghezza delle precipitazioni. La nebbia gelata usa una velatura più fredda e densa. Le precipitazioni gelate e la grandine mantengono l'approssimazione grafica descritta sotto.

Gli sfondi sono **autonomi e già composti**, senza riferimenti a file esterni. Per uno sfondo statico basta caricare l'SVG prescelto. Se vuoi modificare i livelli in modo indipendente, usa i gruppi interni, con questi identificatori stabili quando il livello è presente:

| Gruppo | Fattore parallasse |
| --- | ---: |
| `layer_0_sky` | 0,00 |
| `layer_1_celestial` | 0,10 |
| `layer_2_distant_clouds` | 0,30 |
| `layer_4_main_clouds` | 0,70 |
| `layer_5_precipitation` | 1,00 |
| `layer_5_fog` | 1,00 |
| `layer_5_lightning` | 1,00 |

I gruppi assenti nella condizione selezionata non vengono creati. Ogni gruppo espone anche `data-depth` e `data-parallax`; il blocco `metadata` indica fascia oraria, condizione e codici WMO. Questi attributi descrivono la struttura: **non applicano animazioni da soli**. Per spostare i gruppi serve un renderer che consenta di accedervi, ad esempio un SVG inline; un elemento immagine unico non espone i singoli gruppi al launcher.

Nei file con temporale il fulmine è visibile come istante illustrato: in un renderer animato imposta subito l'opacità di `layer_5_lightning` a zero e rendilo visibile soltanto durante il lampo. Negli sfondi completi le posizioni degli astri sono illustrative, basse all'alba e al tramonto; per seguire continuamente l'orario modifica il gruppo celeste. I file rimangono statici finché il launcher non li anima.

Gli sfondi completi hanno lo stesso canvas quadrato 3840 × 3840 dei livelli base. Il ritaglio 16:9 può escludere gli estremi superiore e inferiore; per la nebbia scegli l'allineamento al bordo inferiore oppure usa i livelli base separati per controllarne il ritaglio indipendentemente. Con il parallasse, applica anche agli sfondi composti l'overscan e i limiti di movimento descritti sotto.

## 1. Struttura e ordine dei livelli

I 19 livelli base utilizzabili dal launcher si trovano in `layers/`; i 120 sfondi composti si trovano in `backgrounds/`. L'ordine di disegno è quello della tabella, dal fondo al primo piano. Le velocità sono moltiplicatori dello spostamento, non velocità in pixel al secondo.

| Livello | Profondità logica | Contenuto | Fattore parallasse |
| --- | ---: | --- | ---: |
| 0 | −3 | Un solo cielo | 0,00 |
| 1 | −2 | Sole oppure luna; stelle serali/notturne | 0,10 |
| 2 | −1 | Nuvole lontane | 0,30 |
| 3 | 0 | Eventuale paesaggio aggiunto in futuro | 0,50 |
| 4 | 1 | Cumuli, copertura grigia oppure temporalesca | 0,70 |
| 5 | 2 | Pioggia o neve; nebbia; fulmine quando necessario | 1,00 |

Il livello 3 è opzionale e non contiene asset in questo pacchetto. Se il framework non ammette indici negativi, usa semplicemente indici di disegno da 0 a 5, mantenendo questo ordine.

## 2. Inventario dei 19 asset

### Cieli — livello 0

| File | Aspetto / impiego |
| --- | --- |
| `sky_dawn_peach_lilac.svg` | Alba: pesca, lilla e blu tenue. |
| `sky_day_vibrant_blue.svg` | Giorno: azzurro intenso e luminoso. |
| `sky_afternoon_warm_blue_gold.svg` | Pomeriggio: azzurro più caldo e oro all'orizzonte. |
| `sky_sunset_orange_violet.svg` | Tramonto: arancione, corallo e viola. |
| `sky_evening_cobalt_magenta.svg` | Sera: cobalto e magenta. |
| `sky_night_deep_navy.svg` | Notte: blu navy scuro. |

### Corpi celesti — livello 1

| File | Aspetto / impiego |
| --- | --- |
| `celestial_sun_radiant_glow.svg` | Disco solare con alone e raggi sottili. |
| `celestial_moon_full_luminous.svg` | Luna piena stilizzata con alone e crateri; non rappresenta la fase reale. |
| `celestial_stars_varied_brightness.svg` | Campo di stelle con dimensioni e opacità diverse. Solo sera e notte. |

Sole e luna sono disegnati intorno al punto `(2560, 1320)` del canvas. Puoi spostare il loro contenitore per cambiarne la posizione nel cielo.

### Nuvole — livelli 2 e 4

| File | Livello | Aspetto / impiego |
| --- | ---: | --- |
| `clouds_distant_delicate_white.svg` | 2 | Nuvole sottili e lontane, con ampie zone trasparenti. |
| `clouds_scattered_volumetric_cumulus.svg` | 4 | Cumuli separati, sommità chiare e ombre azzurro-lavanda. |
| `clouds_overcast_silver_grey.svg` | 4 | Copertura grigia continua nella parte alta del canvas. |
| `clouds_thunderstorm_dark_indigo.svg` | 4 | Copertura scura, stratificata, con bordi e ombre blu. |

La parte inferiore delle coperture lascia intravedere il cielo. Per i temporali, scurisci anche il cielo con un overlay blu-nero, ad esempio opacità 0,4; evita di scurire precipitazioni e fulmine con lo stesso overlay. Al tramonto puoi applicare una tinta calda solo ai layer delle nuvole; di notte riducine luminosità e saturazione.

### Fenomeni — livello 5

| File | Aspetto / impiego |
| --- | --- |
| `precipitation_rain_light_diagonal.svg` | Pioggia leggera con tratti sottili inclinati. |
| `precipitation_rain_heavy_diagonal.svg` | Pioggia più densa con piccoli schizzi nella parte bassa. |
| `precipitation_snow_light_soft_flakes.svg` | Fiocchi radi, di dimensioni e opacità diverse. |
| `precipitation_snow_heavy_diagonal_blizzard.svg` | Neve fitta, con tratti obliqui per l'effetto tormenta. |
| `effect_lightning_branching_blue_white.svg` | Fulmine ramificato bianco-azzurro con alone. |
| `effect_fog_low_lying_translucent.svg` | Banchi di nebbia nella parte inferiore, con bordi sfumati. |

Il fulmine usa una sfocatura SVG per l'alone; la nebbia la usa per ammorbidire i banchi. Se il renderer del launcher non supporta `feGaussianBlur`, verifica l'aspetto: il fulmine mantiene comunque i tracciati principali, mentre la nebbia avrà bordi più netti. Gli altri asset non richiedono filtri SVG.

## 3. Ora del giorno e Open-Meteo

Richiedi le coordinate della località scelta, `current=weather_code,is_day` e `daily=sunrise,sunset`. Usa `timezone=auto`, `timeformat=unixtime`, `past_days=1` e `forecast_days=2`: avrai anche gli eventi solari adiacenti alla notte corrente. Confronta i timestamp in secondi con `Date.now() / 1000`; formatta le etichette con il fuso della località.

Esempio di richiesta per Roma, con coordinate da sostituire per altre località:

```text
https://api.open-meteo.com/v1/forecast?latitude=41.9028&longitude=12.4964&current=weather_code,is_day&daily=sunrise,sunset&timezone=auto&timeformat=unixtime&past_days=1&forecast_days=2
```

Le seguenti soglie sono una proposta visiva del pacchetto, non fasce orarie fornite dall'API. Applica le condizioni nell'ordine indicato per risolvere anche eventuali sovrapposizioni in giornate corte:

1. **Alba:** da 45 minuti prima a 45 minuti dopo il sorgere del sole.
2. **Tramonto:** da 60 minuti prima a 20 minuti dopo il tramonto.
3. **Sera:** dai 20 ai 90 minuti dopo il tramonto.
4. **Giorno / pomeriggio:** negli altri istanti fra alba e tramonto, usa il cielo di giorno fino al 60% dell'intervallo solare, poi quello pomeridiano.
5. **Notte:** tutti gli altri istanti, fino all'alba successiva.

Mostra il sole negli intervalli diurni e durante il tramonto, la luna di notte. All'alba puoi far comparire gradualmente il sole. Fai comparire le stelle in sera/notte e nascondile durante il passaggio all'alba. Nascondi gli astri quando la copertura è completa, quando piove o nevica, o con nebbia densa.

Per una traiettoria solare illustrativa, calcola `p = clamp((now − sunrise) / (sunset − sunrise), 0, 1)` e usa una curva con altezza proporzionale a `sin(π × p)`: il sole sarà basso ai due estremi e alto al centro. Per la luna puoi animare una traiettoria simile fra tramonto e alba successiva; è una scelta scenografica, non una posizione astronomica calcolata.

Se i dati solari sono assenti, non finiti o incoerenti, usa temporaneamente `is_day` per scegliere giorno/notte. Aggiorna la selezione al cambio di data della località. Se la rete non risponde, conserva l'ultima condizione valida e continua ad aggiornare la fase oraria con gli eventi solari già disponibili; alla prima esecuzione senza dati, mostra un cielo neutro senza precipitazioni.

## 4. Associazione delle condizioni WMO

Questa tabella indica una scelta grafica per i codici restituiti dall'API. “Pioggia intensa” e “neve intensa” identificano il layer da usare, raggruppando anche intensità intermedie. Non sovrapporre i due layer di pioggia o i due layer di neve.

| Codici | Layer di nuvole / copertura | Fenomeno | Astri |
| --- | --- | --- | --- |
| 0 | Nessuno | Nessuno | Visibili |
| 1 | Nuvole lontane | Nessuno | Visibili |
| 2 | Nuvole lontane + cumuli | Nessuno | Visibili |
| 3 | Copertura grigia | Nessuno | Nascosti |
| 45, 48 | Copertura grigia attenuata | Nebbia | Nascosti |
| 51, 53, 55, 56, 57 | Copertura grigia | Pioggia leggera | Nascosti |
| 61, 66, 80 | Copertura grigia | Pioggia leggera | Nascosti |
| 63, 65, 67, 81, 82 | Copertura temporalesca | Pioggia intensa | Nascosti |
| 71, 77, 85 | Copertura grigia | Neve leggera | Nascosti |
| 73, 75, 86 | Copertura grigia | Neve intensa | Nascosti |
| 95, 96, 97, 99 | Copertura temporalesca | Pioggia intensa + fulmine intermittente | Nascosti |

Per grandine e precipitazioni gelate, il pacchetto riutilizza pioggia o neve: non contiene un asset dedicato al ghiaccio. Con un codice sconosciuto conserva il cielo corretto per l'orario e disattiva i fenomeni, senza inventare una condizione.

Esempi richiesti nel progetto iniziale:

- **Sereno di giorno:** cielo di giorno + sole.
- **Poco nuvoloso al tramonto:** cielo del tramonto + sole basso + nuvole lontane + cumuli, con tinta calda.
- **Pioggia moderata/forte di notte:** cielo notturno + copertura temporalesca + pioggia intensa; astri nascosti.
- **Temporale di giorno:** cielo diurno scurito + copertura temporalesca + pioggia intensa + fulmine intermittente.

## 5. Parallasse e adattamento allo schermo

I canvas quadrati permettono ritaglio e movimento anche su uno schermo 16:9. Renderizza i livelli su un'area più grande del viewport e ritaglia ciò che esce dai bordi. Per non deformare le nuvole, mantieni le proporzioni. La nebbia richiede **allineamento al bordo inferiore**: con un ritaglio centrato su 16:9 può rimanere fuori dall'inquadratura.

Per un viewport `W × H` e spostamenti massimi `Mx, My`, una dimensione sicura del lato quadrato renderizzato è:

```text
S = max(W + 2 × Mx, H + 2 × My)
```

Centra il canvas degli altri livelli. Per la nebbia, allinea la base del canvas a `H + My`, lasciando comunque il margine di movimento inferiore. Il cielo è immobile e può semplicemente coprire il viewport.

Collega l'input allo scroll delle pagine Home o all'orientamento, se disponibile:

```text
offsetX = −clamp(inputX, −Mx, Mx) × fattoreDelLivello
offsetY = −clamp(inputY, −My, My) × fattoreDelLivello
```

Con input orizzontale di 100 px: cielo 0 px, astri −10 px, nuvole lontane −30 px, paesaggio eventuale −50 px, nuvole vicine −70 px, fenomeni −100 px. Se combini scroll e sensori, limita lo spostamento complessivo ai margini effettivamente disponibili. Smussa il movimento per evitare scatti; su TV privi di sensori usa solo lo scroll.

Aggiungi il movimento delle precipitazioni a un contenitore interno distinto da quello del parallasse. Pioggia: scorrimento diagonale verso il basso; neve leggera: più lenta; tormenta: diagonale più pronunciata. Per animazioni continue senza salti, ripeti il layer su due copie adiacenti e spostale di un'intera altezza renderizzata prima di ricominciare. Aumenta i margini laterali se aggiungi deriva orizzontale. Le distribuzioni non sono texture con bordi perfettamente raccordati: verifica la giunzione oppure usa un emettitore di particelle del launcher per un flusso continuo.

Lascia il fulmine a opacità zero e mostrane brevemente il layer a intervalli irregolari, ad esempio ogni 6–14 secondi. Per il cambio di cielo o condizione, usa una dissolvenza di 1–2 secondi fra i gruppi vecchio e nuovo. Con movimento ridotto, mantieni i layer statici e disattiva i lampeggi.

## 6. Integrazione e prestazioni

Carica il cielo corrente e solo i layer necessari. Tieni in memoria la fase successiva per la dissolvenza, poi rilascia quelli inutilizzati. Sei superfici RGBA complete a 3840 × 3840 possono occupare circa 338 MiB prima di copie e buffer aggiuntivi: il peso ridotto dei file SVG non equivale al consumo delle superfici renderizzate. Sui dispositivi meno potenti renderizza nuvole e fenomeni a una risoluzione inferiore e applica il movimento come trasformazione del contenitore.

Usa un renderer SVG compatibile con gradienti, trasparenze e, per nebbia/alone del fulmine, filtri Gaussian blur. Se inserisci più SVG direttamente nello stesso documento HTML, assegna un prefisso univoco agli `id` e ai rispettivi riferimenti `url(#...)`; i file caricati separatamente non condividono questi identificatori. La tavola `weather_scene_examples.svg` contiene già identificatori separati ed è autonoma.

Il pacchetto fornisce 19 livelli base, 120 sfondi completi e istruzioni: non include un launcher compilato né un collegamento attivo a Open-Meteo. Non essendoci codice preesistente nella cartella SalottoTV, l'integrazione va effettuata con il renderer e i controller del framework scelto.

## Riferimento per i dati meteo

Parametri API, timestamp e codici delle condizioni verificati nella [documentazione ufficiale Open-Meteo](https://open-meteo.com/en/docs). La selezione dei layer, le soglie orarie e i valori di animazione sopra sono scelte di questo pacchetto.
