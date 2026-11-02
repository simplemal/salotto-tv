# SalottoTV — Specifica matematica delle animazioni meteo

Questa specifica copre **tutte le 120 combinazioni presenti nei file SVG**: 6 fasce orarie × 20 condizioni. Descrive l'ingresso nella Home e la successiva animazione continua. I parametri sono scelte di animazione, non misurazioni meteorologiche. Gli SVG attuali contengono gli elementi grafici; il launcher deve applicare queste formule. Questo documento non aggiunge automaticamente animazioni ai file.

## 1. Struttura dei file e coordinate

Il pacchetto completo si trova in `SalottoTV/assets/background-meteo-anime/`. I 19 livelli base restano nella sottocartella `layers/`; le 120 composizioni restano in `backgrounds/<fase>/`. I collegamenti di questo documento e dell'indice sono relativi a questa cartella.

Ogni composizione contiene solo i gruppi necessari alla propria condizione. Non ci sono colline, montagne o paesaggi: l'ingresso dal basso viene usato per la nebbia. Le nuvole possono entrare dall'alto.

| Simbolo | Significato |
| --- | --- |
| `W, H` | Larghezza e altezza visibili, in pixel logici |
| `t` | Secondi attivi trascorsi dall'ingresso nella Home, da un orologio monotono |
| `t_s` | Fine dell'introduzione della specifica combinazione |
| `τ = max(0, t − t_s)` | Tempo della fase continua |
| `p_i` | Fattore di parallasse del gruppo |
| `b_min, b_max` | Estremi verticali del rettangolo visivo del gruppo, dopo scala e ritaglio, prima dell'animazione |
| `a_x, a_y` | Ampiezze massime della tabella, in percentuale di W e H, prima del fattore `p_i` |
| `B` | Periodo di base della tabella, in secondi |
| `β_i` | Opacità già presente nel disegno, da preservare |

Le formule sono in coordinate dello schermo. Se si applicano trasformazioni in coordinate SVG, convertire prima gli spostamenti con l'inversa della trasformazione di vista. Con scala uniforme `s`, uno spostamento di `d` pixel equivale a `d/s` unità SVG. Conservare tutte le trasformazioni già presenti nel file; aggiungere contenitori per ingresso, parallasse e moto locale invece di sovrascriverle.

| ID del gruppo | p_i | Introduzione | Moltiplicatore di periodo c_i |
| --- | ---: | --- | ---: |
| `layer_0_sky` | 0 | Visibile subito, fermo | — |
| `layer_1_celestial` | 0,10 | Dissolvenza | 1,80 |
| `layer_2_distant_clouds` | 0,30 | Dall'alto | 1,35 |
| `layer_4_main_clouds` | 0,70 | Dall'alto | 1,00 |
| `layer_5_precipitation` | 1,00 | Dissolvenza dopo le nuvole | 0,80 |
| `layer_5_fog` | 1,00 | Dal basso | 1,12 |
| `layer_5_lightning` | 1,00 | Nascosto fino al primo lampo | 1,00 |

Le abbreviazioni nelle tabelle sono: `C` = celestial, `D` = distant_clouds, `M` = main_clouds, `P` = precipitation, `F` = fog, `L` = lightning. Il cielo è sempre presente. L'assenza di una sigla significa che quel gruppo non va creato né animato.

## 2. Curva di ingresso senza scatti

Usare ovunque la funzione quintica:

```text
clamp01(x) = min(1, max(0, x))
u(t; d, T) = clamp01((t − d) / T)
E(u) = 6u^5 − 15u^4 + 10u^3
```

`E(0)=0`, `E(1)=1`; velocità e accelerazione sono nulle ai due estremi. L'introduzione risulta morbida e termina senza rimbalzi.

Il cielo è già completamente visibile al primo fotogramma. Con margine visivo `m = 0,02H`, ricavare l'offset iniziale per ogni gruppo da far entrare:

```text
e_top    = −b_max − m
e_bottom = H − b_min + m
entryY(t) = e_direction × [1 − E(u(t; delay, duration))]
entryAlpha(t) = E(u(t; delay, duration))
```

Queste distanze portano davvero il gruppo fuori dallo schermo, qualunque sia il ritaglio; non usare un offset fisso uguale per tutti. Misurare i limiti visivi includendo tratti, bagliori e sfocature. Applicare il clipping al viewport, non a ogni singolo livello in movimento.

Per ciascuna riga della tabella:

- **C:** delay `0,12 s`, durata `1,40 s`; sola dissolvenza, nessun ingresso da fuori schermo.
- **D:** delay `0,20 s`, durata `0,90T`; ingresso dall'alto.
- **M:** delay `0,45 s`, durata `T`; ingresso dall'alto.
- **F:** delay `0,65 s`, durata `1,10T`; ingresso dal basso.
- **P:** delay `d_P` indicato nella riga, durata `1,20 s`; sola dissolvenza. Le particelle iniziano a cadere durante questa dissolvenza.
- **L:** opacità zero già prima del primo fotogramma; il primo lampo arriva almeno 10 secondi dopo `t_s`.

`T` è la durata nuvole della riga; per le scene senza nuvole è zero e non si usa. `d_P` è la fine dell'ingresso delle nuvole più `0,15 s`. `t_s` è il massimo dei tempi di fine dei gruppi presenti, più `0,10 s`. Nelle scene serene è quindi `1,62 s`. I valori riportati nelle righe sono già calcolati.

## 3. Moto lentissimo dopo l'ingresso

Definire il raccordo `g(τ) = E(clamp01(τ / 12))`. Nei primi 12 secondi dopo l'introduzione il moto emerge gradualmente da zero, senza interruzione della velocità. Per il gruppo i:

```text
Axi = p_i × (a_x / 100) × W
Ayi = p_i × (a_y / 100) × H
Pi  = c_i × B

idleX_i(τ) = g(τ) × Axi × [
  0,72 sin(2π τ / Pi + φ_i) +
  0,28 sin(2π τ / (√2 Pi) + ψ_i)
]

idleY_i(τ) = g(τ) × Ayi × [
  0,65 sin(2π τ / (√3 Pi) + χ_i) +
  0,35 sin(2π τ / (√5 Pi) + η_i)
]
```

Le ampiezze rimangono sempre entro `±Axi` e `±Ayi`. I periodi non commensurabili evitano un ciclo globale breve e riconoscibile. Il movimento può continuare per ore o giorni senza raggiungere un bordo per deriva cumulativa. Non è una promessa di non ripetizione matematica infinita su numeri digitali: non si azzera il tempo ogni pochi minuti e non si accumula uno spostamento illimitato.

Usare fasi deterministiche diverse per ogni combinazione e gruppo:

```text
key = "<phase>/<weather>/<groupID>/<x1|x2|y1|y2>"
h(key) = uint32_be(primi 4 byte di SHA-256 della stringa UTF-8) / 2^32
φ_i, ψ_i, χ_i, η_i = 2π × h(della rispettiva key)
```

Calcolare hash e parametri una volta al caricamento. Non generare casualità a ogni fotogramma. Le quattro componenti non devono condividere la stessa fase.

### Parallasse legato all'utente

Normalizzare scroll e sensori in `q_x,q_y ∈ [−1,1]`. Se si combinano più input, limitarne la somma allo stesso intervallo. Smussare con il sistema criticamente smorzato, per ciascun asse:

```text
q_s'' + 2ω q_s' + ω² q_s = ω² q_target
ω = 5 s⁻¹
Mx = 0,035W ; My = 0,018H
inputX_i = −p_i × Mx × q_sx
inputY_i = −p_i × My × q_sy

X_i(t) = g(τ) × inputX_i(t) + idleX_i(τ)
Y_i(t) = entryY_i(t) + g(τ) × inputY_i(t) + idleY_i(τ)
```

Partire da posizione e velocità zero. Usare un integratore stabile o la soluzione esatta per input costante nel singolo fotogramma; evitare Euler esplicito con intervalli lunghi. Se la TV non dispone di sensori, lo scroll è sufficiente; anche con input zero resta il moto autonomo.

### Margini e ritaglio

Per il moto continuo, i margini per lato devono soddisfare:

```text
Rx >= Mx + (a_x / 100)W
Ry >= My + (a_y / 100)H
S  >= max(W + 2Rx, H + 2Ry)
```

`S` è il lato del canvas quadrato renderizzato, con scala uniforme e margini centrati. In alternativa, calcolare margini diversi per gruppo con i rispettivi `p_i`. Il cielo resta immobile e copre sempre il viewport. L'introduzione può rivelare il cielo mentre gli altri gruppi sono ancora fuori campo: è il comportamento previsto.

Per la nebbia, verificare l'allineamento in basso su 16:9 e applicare il ritaglio corretto prima di misurare `b_min,b_max`. In una composizione unica il ritaglio è condiviso; per un controllo indipendente della nebbia usare i livelli separati o contenitori con una propria trasformazione di vista. Non allungare il disegno per riempire lo schermo.

## 4. Precipitazioni: flusso continuo senza riavvolgimento visibile

Le distribuzioni SVG non sono texture perfettamente raccordate: una semplice traslazione e ripartenza dell'intero layer può mostrare un salto. La soluzione prescritta è trattare ogni `path` di pioggia o ogni `circle/path` di neve come particella, preservandone il tratto e aggiungendo un contenitore di traslazione.

La colonna `v` riporta la velocità verticale media in percentuale di H al secondo. Non applicare i periodi lentissimi di nuvole e parallasse alla caduta della pioggia. Il gruppo P ha il suo moto lento; all'interno, le particelle cadono continuamente.

Per ogni particella j, con tempo locale `ρ = max(0,t − d_P)` e hash `h_j = h("<phase>/<weather>/particle/<j>")`:

```text
vy_j = (v / 100)H × (0,75 + 0,50h_j)
vx_j = −0,24vy_j           per drizzle/rain
vx_j = −0,12vy_j           per light/moderate snow
vx_j = −0,65vy_j           per heavy snow o heavy snow showers

wrap(z, a, b) = a + ((z − a) mod (b − a))
y_j(ρ) = wrap(y_j0 + vy_j ρ, −L, H + L)
x_j(ρ) = wrap(x_j0 + vx_j ρ + sway_j(ρ), −L, W + L)
```

Il modulo deve restituire un valore non negativo anche per input negativi. Per pioggia e drizzle, `sway_j = 0`. Per neve:

```text
Aj = W × (0,002 + 0,004h_j)
Pj = 7 + 13h_j   secondi
sway_j(ρ) = Aj × [sin(2πρ/Pj + 2πh_j) − sin(2πh_j)]
```

Usare come origine il primo punto del tratto o il centro del fiocco, convertito in coordinate dello schermo. Impostare `L` maggiore dell'estensione massima della particella, della sua oscillazione e dello spostamento complessivo del gruppo, più 8 pixel: il riciclo avviene completamente fuori dalla zona visibile. Per la neve, il termine sway può raggiungere `2Aj`, quindi includere almeno quella quantità nel margine. Il cambio di coordinate al wrap non deve essere interpolato da una transizione automatica del framework.

Usare come posizioni iniziali quelle già distribuite nel disegno. Quando particelle disegnate fuori dal canvas vengono normalizzate, farlo prima del primo fotogramma visibile. Non aggiungere gli schizzi del layer base a questo algoritmo come gocce in caduta: negli sfondi completi i tratti P sono già solo precipitazioni; con il livello base pesante separare gli schizzi a terra.

## 5. Nebbia, stelle e fulmini

**Nebbia:** oltre alla deriva lenta del gruppo F, una variazione di densità molto lieve:

```text
fogFactor(τ) = 1 − g(τ) + g(τ) × [0,94 + 0,06 sin(2π τ / (√7 B) + φ_F)]
alpha_F(t) = entryAlpha_F(t) × fogFactor(τ)
```

Applicare il fattore all'opacità del contenitore esterno, preservando quelle interne. A τ=0 il fattore è 1 e non produce un salto dopo l'introduzione.

**Stelle:** solo evening/night e soltanto se C esiste. Animare i singoli cerchi del gruppo delle stelle, non l'intero gruppo C che contiene anche la luna. Identificare e conservare un riferimento al gruppo stellare durante il caricamento. Per ogni stella j:

```text
starFactor_j(τ) = 1 − g(τ) + g(τ) × [0,88 + 0,12 sin(2π τ / (17 + 23h_j) + 2πh_j)]
alpha_star_j = alpha_original_j × starFactor_j
```

Mantenere la dissolvenza C sul contenitore esterno. Non alterare l'opacità della luna con questo fattore. Nelle altre fasce orarie non attivare stelle.

**Fulmini:** solo thunderstorm, per tutte le sei fasce orarie. Il gruppo L parte con opacità zero; gli altri gruppi non diventano bianchi. Definire i tempi dei lampi:

```text
s_0 = t_s + 10
s_(n+1) = s_n + 11 + 9h("<phase>/thunderstorm/flash/<n>")
```

Definire una singola pulsazione con salita, eventuale plateau e discesa:

```text
pulse(z; a,b,c,d) = E(clamp01((z−a)/(b−a))) × [1−E(clamp01((z−c)/(d−c)))]
z = t − s_n
alpha_L(t) = 0,65 pulse(z;0,0.025,0.045,0.12)
           + 0,30 pulse(z;0.18,0.205,0.22,0.30)
```

Fuori dalle due pulsazioni l'opacità è zero. Le pulsazioni non si sovrappongono: il picco non supera 0,65. Fra un lampo e il successivo continua il moto di pioggia e nuvole.

## 6. Parametri espliciti per tutte le 120 combinazioni

Per ciascuna riga: applicare la sequenza del §2 ai soli gruppi elencati, poi il moto del §3 con i parametri numerici della riga. Attivare il §4 se è presente P; il §5 per F, L e per le stelle nelle sole fasce evening/night. Nessun parametro implicito dipende da un file mancante.

- `T`: durata dell'ingresso delle nuvole in secondi; D usa 0,90T e F usa 1,10T.
- `d_P`: istante di inizio della dissolvenza delle precipitazioni, in secondi; `—` se assenti.
- `t_s`: istante di inizio della fase continua, in secondi.
- `a_x, a_y`: ampiezze percentuali per il livello con p=1; per gli altri livelli moltiplicare per p.
- `B`: periodo base in secondi; non è un riavvio della scena.
- `v`: velocità delle precipitazioni in %H/s; `—` se assenti.

Le variazioni tra alba, giorno, pomeriggio, tramonto, sera e notte sono intenzionali: sera e notte hanno ingressi più lenti, deriva più contenuta e periodi più lunghi. I parametri derivano dal profilo meteo con moltiplicatori `(durata, ampiezza, periodo)` rispettivamente: dawn `(1,12;0,85;1,15)`, day `(1;1;1)`, afternoon `(1,06;0,95;1,08)`, sunset `(1,18;0,85;1,20)`, evening `(1,24;0,75;1,30)`, night `(1,30;0,70;1,40)`. I valori della tabella, già arrotondati, sono quelli normativi da implementare.


### Dawn

| Combinazione SVG | Gruppi | T (s) | d_P (s) | t_s (s) | a_x (%W) | a_y (%H) | B (s) | v (%H/s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [dawn / clear_sky](backgrounds/dawn/weather_dawn_clear_sky.svg) | C | — | — | 1.62 | 0.255 | 0.102 | 621 | — |
| [dawn / mostly_clear](backgrounds/dawn/weather_dawn_mostly_clear.svg) | C + D | 3.136 | — | 3.122 | 0.765 | 0.238 | 483 | — |
| [dawn / partly_cloudy](backgrounds/dawn/weather_dawn_partly_cloudy.svg) | C + D + M | 3.584 | — | 4.134 | 1.105 | 0.357 | 414 | — |
| [dawn / overcast](backgrounds/dawn/weather_dawn_overcast.svg) | M | 3.92 | — | 4.47 | 0.722 | 0.187 | 552 | — |
| [dawn / fog](backgrounds/dawn/weather_dawn_fog.svg) | M + F | 4.256 | — | 5.432 | 0.935 | 0.297 | 690 | — |
| [dawn / freezing_fog](backgrounds/dawn/weather_dawn_freezing_fog.svg) | M + F | 4.704 | — | 5.924 | 0.68 | 0.212 | 828 | — |
| [dawn / light_drizzle](backgrounds/dawn/weather_dawn_light_drizzle.svg) | M + P | 3.696 | 4.296 | 5.596 | 0.68 | 0.187 | 552 | 20 |
| [dawn / moderate_drizzle](backgrounds/dawn/weather_dawn_moderate_drizzle.svg) | M + P | 3.808 | 4.408 | 5.708 | 0.765 | 0.212 | 517.5 | 25 |
| [dawn / heavy_drizzle](backgrounds/dawn/weather_dawn_heavy_drizzle.svg) | M + P | 4.032 | 4.632 | 5.932 | 0.85 | 0.238 | 483 | 30 |
| [dawn / light_rain](backgrounds/dawn/weather_dawn_light_rain.svg) | M + P | 3.696 | 4.296 | 5.596 | 0.85 | 0.238 | 483 | 45 |
| [dawn / moderate_rain](backgrounds/dawn/weather_dawn_moderate_rain.svg) | M + P | 3.92 | 4.52 | 5.82 | 1.02 | 0.272 | 448.5 | 60 |
| [dawn / heavy_rain](backgrounds/dawn/weather_dawn_heavy_rain.svg) | M + P | 4.256 | 4.856 | 6.156 | 1.19 | 0.323 | 414 | 80 |
| [dawn / light_snow](backgrounds/dawn/weather_dawn_light_snow.svg) | M + P | 4.032 | 4.632 | 5.932 | 0.807 | 0.297 | 621 | 3 |
| [dawn / moderate_snow](backgrounds/dawn/weather_dawn_moderate_snow.svg) | M + P | 4.256 | 4.856 | 6.156 | 0.935 | 0.357 | 586.5 | 4.5 |
| [dawn / heavy_snow](backgrounds/dawn/weather_dawn_heavy_snow.svg) | M + P | 4.592 | 5.192 | 6.492 | 1.105 | 0.425 | 552 | 6 |
| [dawn / light_rain_showers](backgrounds/dawn/weather_dawn_light_rain_showers.svg) | C + D + M + P | 3.472 | 4.072 | 5.372 | 1.062 | 0.306 | 448.5 | 48 |
| [dawn / heavy_rain_showers](backgrounds/dawn/weather_dawn_heavy_rain_showers.svg) | M + P | 4.032 | 4.632 | 5.932 | 1.275 | 0.357 | 379.5 | 85 |
| [dawn / light_snow_showers](backgrounds/dawn/weather_dawn_light_snow_showers.svg) | C + D + M + P | 3.808 | 4.408 | 5.708 | 1.02 | 0.34 | 552 | 3.5 |
| [dawn / heavy_snow_showers](backgrounds/dawn/weather_dawn_heavy_snow_showers.svg) | M + P | 4.48 | 5.08 | 6.38 | 1.275 | 0.468 | 483 | 7 |
| [dawn / thunderstorm](backgrounds/dawn/weather_dawn_thunderstorm.svg) | M + P + L | 4.704 | 5.304 | 6.604 | 1.36 | 0.383 | 379.5 | 90 |

### Day

| Combinazione SVG | Gruppi | T (s) | d_P (s) | t_s (s) | a_x (%W) | a_y (%H) | B (s) | v (%H/s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [day / clear_sky](backgrounds/day/weather_day_clear_sky.svg) | C | — | — | 1.62 | 0.3 | 0.12 | 540 | — |
| [day / mostly_clear](backgrounds/day/weather_day_mostly_clear.svg) | C + D | 2.8 | — | 2.82 | 0.9 | 0.28 | 420 | — |
| [day / partly_cloudy](backgrounds/day/weather_day_partly_cloudy.svg) | C + D + M | 3.2 | — | 3.75 | 1.3 | 0.42 | 360 | — |
| [day / overcast](backgrounds/day/weather_day_overcast.svg) | M | 3.5 | — | 4.05 | 0.85 | 0.22 | 480 | — |
| [day / fog](backgrounds/day/weather_day_fog.svg) | M + F | 3.8 | — | 4.93 | 1.1 | 0.35 | 600 | — |
| [day / freezing_fog](backgrounds/day/weather_day_freezing_fog.svg) | M + F | 4.2 | — | 5.37 | 0.8 | 0.25 | 720 | — |
| [day / light_drizzle](backgrounds/day/weather_day_light_drizzle.svg) | M + P | 3.3 | 3.9 | 5.2 | 0.8 | 0.22 | 480 | 20 |
| [day / moderate_drizzle](backgrounds/day/weather_day_moderate_drizzle.svg) | M + P | 3.4 | 4 | 5.3 | 0.9 | 0.25 | 450 | 25 |
| [day / heavy_drizzle](backgrounds/day/weather_day_heavy_drizzle.svg) | M + P | 3.6 | 4.2 | 5.5 | 1 | 0.28 | 420 | 30 |
| [day / light_rain](backgrounds/day/weather_day_light_rain.svg) | M + P | 3.3 | 3.9 | 5.2 | 1 | 0.28 | 420 | 45 |
| [day / moderate_rain](backgrounds/day/weather_day_moderate_rain.svg) | M + P | 3.5 | 4.1 | 5.4 | 1.2 | 0.32 | 390 | 60 |
| [day / heavy_rain](backgrounds/day/weather_day_heavy_rain.svg) | M + P | 3.8 | 4.4 | 5.7 | 1.4 | 0.38 | 360 | 80 |
| [day / light_snow](backgrounds/day/weather_day_light_snow.svg) | M + P | 3.6 | 4.2 | 5.5 | 0.95 | 0.35 | 540 | 3 |
| [day / moderate_snow](backgrounds/day/weather_day_moderate_snow.svg) | M + P | 3.8 | 4.4 | 5.7 | 1.1 | 0.42 | 510 | 4.5 |
| [day / heavy_snow](backgrounds/day/weather_day_heavy_snow.svg) | M + P | 4.1 | 4.7 | 6 | 1.3 | 0.5 | 480 | 6 |
| [day / light_rain_showers](backgrounds/day/weather_day_light_rain_showers.svg) | C + D + M + P | 3.1 | 3.7 | 5 | 1.25 | 0.36 | 390 | 48 |
| [day / heavy_rain_showers](backgrounds/day/weather_day_heavy_rain_showers.svg) | M + P | 3.6 | 4.2 | 5.5 | 1.5 | 0.42 | 330 | 85 |
| [day / light_snow_showers](backgrounds/day/weather_day_light_snow_showers.svg) | C + D + M + P | 3.4 | 4 | 5.3 | 1.2 | 0.4 | 480 | 3.5 |
| [day / heavy_snow_showers](backgrounds/day/weather_day_heavy_snow_showers.svg) | M + P | 4 | 4.6 | 5.9 | 1.5 | 0.55 | 420 | 7 |
| [day / thunderstorm](backgrounds/day/weather_day_thunderstorm.svg) | M + P + L | 4.2 | 4.8 | 6.1 | 1.6 | 0.45 | 330 | 90 |

### Afternoon

| Combinazione SVG | Gruppi | T (s) | d_P (s) | t_s (s) | a_x (%W) | a_y (%H) | B (s) | v (%H/s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [afternoon / clear_sky](backgrounds/afternoon/weather_afternoon_clear_sky.svg) | C | — | — | 1.62 | 0.285 | 0.114 | 583.2 | — |
| [afternoon / mostly_clear](backgrounds/afternoon/weather_afternoon_mostly_clear.svg) | C + D | 2.968 | — | 2.971 | 0.855 | 0.266 | 453.6 | — |
| [afternoon / partly_cloudy](backgrounds/afternoon/weather_afternoon_partly_cloudy.svg) | C + D + M | 3.392 | — | 3.942 | 1.235 | 0.399 | 388.8 | — |
| [afternoon / overcast](backgrounds/afternoon/weather_afternoon_overcast.svg) | M | 3.71 | — | 4.26 | 0.807 | 0.209 | 518.4 | — |
| [afternoon / fog](backgrounds/afternoon/weather_afternoon_fog.svg) | M + F | 4.028 | — | 5.181 | 1.045 | 0.332 | 648 | — |
| [afternoon / freezing_fog](backgrounds/afternoon/weather_afternoon_freezing_fog.svg) | M + F | 4.452 | — | 5.647 | 0.76 | 0.237 | 777.6 | — |
| [afternoon / light_drizzle](backgrounds/afternoon/weather_afternoon_light_drizzle.svg) | M + P | 3.498 | 4.098 | 5.398 | 0.76 | 0.209 | 518.4 | 20 |
| [afternoon / moderate_drizzle](backgrounds/afternoon/weather_afternoon_moderate_drizzle.svg) | M + P | 3.604 | 4.204 | 5.504 | 0.855 | 0.237 | 486 | 25 |
| [afternoon / heavy_drizzle](backgrounds/afternoon/weather_afternoon_heavy_drizzle.svg) | M + P | 3.816 | 4.416 | 5.716 | 0.95 | 0.266 | 453.6 | 30 |
| [afternoon / light_rain](backgrounds/afternoon/weather_afternoon_light_rain.svg) | M + P | 3.498 | 4.098 | 5.398 | 0.95 | 0.266 | 453.6 | 45 |
| [afternoon / moderate_rain](backgrounds/afternoon/weather_afternoon_moderate_rain.svg) | M + P | 3.71 | 4.31 | 5.61 | 1.14 | 0.304 | 421.2 | 60 |
| [afternoon / heavy_rain](backgrounds/afternoon/weather_afternoon_heavy_rain.svg) | M + P | 4.028 | 4.628 | 5.928 | 1.33 | 0.361 | 388.8 | 80 |
| [afternoon / light_snow](backgrounds/afternoon/weather_afternoon_light_snow.svg) | M + P | 3.816 | 4.416 | 5.716 | 0.902 | 0.332 | 583.2 | 3 |
| [afternoon / moderate_snow](backgrounds/afternoon/weather_afternoon_moderate_snow.svg) | M + P | 4.028 | 4.628 | 5.928 | 1.045 | 0.399 | 550.8 | 4.5 |
| [afternoon / heavy_snow](backgrounds/afternoon/weather_afternoon_heavy_snow.svg) | M + P | 4.346 | 4.946 | 6.246 | 1.235 | 0.475 | 518.4 | 6 |
| [afternoon / light_rain_showers](backgrounds/afternoon/weather_afternoon_light_rain_showers.svg) | C + D + M + P | 3.286 | 3.886 | 5.186 | 1.188 | 0.342 | 421.2 | 48 |
| [afternoon / heavy_rain_showers](backgrounds/afternoon/weather_afternoon_heavy_rain_showers.svg) | M + P | 3.816 | 4.416 | 5.716 | 1.425 | 0.399 | 356.4 | 85 |
| [afternoon / light_snow_showers](backgrounds/afternoon/weather_afternoon_light_snow_showers.svg) | C + D + M + P | 3.604 | 4.204 | 5.504 | 1.14 | 0.38 | 518.4 | 3.5 |
| [afternoon / heavy_snow_showers](backgrounds/afternoon/weather_afternoon_heavy_snow_showers.svg) | M + P | 4.24 | 4.84 | 6.14 | 1.425 | 0.522 | 453.6 | 7 |
| [afternoon / thunderstorm](backgrounds/afternoon/weather_afternoon_thunderstorm.svg) | M + P + L | 4.452 | 5.052 | 6.352 | 1.52 | 0.427 | 356.4 | 90 |

### Sunset

| Combinazione SVG | Gruppi | T (s) | d_P (s) | t_s (s) | a_x (%W) | a_y (%H) | B (s) | v (%H/s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [sunset / clear_sky](backgrounds/sunset/weather_sunset_clear_sky.svg) | C | — | — | 1.62 | 0.255 | 0.102 | 648 | — |
| [sunset / mostly_clear](backgrounds/sunset/weather_sunset_mostly_clear.svg) | C + D | 3.304 | — | 3.274 | 0.765 | 0.238 | 504 | — |
| [sunset / partly_cloudy](backgrounds/sunset/weather_sunset_partly_cloudy.svg) | C + D + M | 3.776 | — | 4.326 | 1.105 | 0.357 | 432 | — |
| [sunset / overcast](backgrounds/sunset/weather_sunset_overcast.svg) | M | 4.13 | — | 4.68 | 0.722 | 0.187 | 576 | — |
| [sunset / fog](backgrounds/sunset/weather_sunset_fog.svg) | M + F | 4.484 | — | 5.682 | 0.935 | 0.297 | 720 | — |
| [sunset / freezing_fog](backgrounds/sunset/weather_sunset_freezing_fog.svg) | M + F | 4.956 | — | 6.202 | 0.68 | 0.212 | 864 | — |
| [sunset / light_drizzle](backgrounds/sunset/weather_sunset_light_drizzle.svg) | M + P | 3.894 | 4.494 | 5.794 | 0.68 | 0.187 | 576 | 20 |
| [sunset / moderate_drizzle](backgrounds/sunset/weather_sunset_moderate_drizzle.svg) | M + P | 4.012 | 4.612 | 5.912 | 0.765 | 0.212 | 540 | 25 |
| [sunset / heavy_drizzle](backgrounds/sunset/weather_sunset_heavy_drizzle.svg) | M + P | 4.248 | 4.848 | 6.148 | 0.85 | 0.238 | 504 | 30 |
| [sunset / light_rain](backgrounds/sunset/weather_sunset_light_rain.svg) | M + P | 3.894 | 4.494 | 5.794 | 0.85 | 0.238 | 504 | 45 |
| [sunset / moderate_rain](backgrounds/sunset/weather_sunset_moderate_rain.svg) | M + P | 4.13 | 4.73 | 6.03 | 1.02 | 0.272 | 468 | 60 |
| [sunset / heavy_rain](backgrounds/sunset/weather_sunset_heavy_rain.svg) | M + P | 4.484 | 5.084 | 6.384 | 1.19 | 0.323 | 432 | 80 |
| [sunset / light_snow](backgrounds/sunset/weather_sunset_light_snow.svg) | M + P | 4.248 | 4.848 | 6.148 | 0.807 | 0.297 | 648 | 3 |
| [sunset / moderate_snow](backgrounds/sunset/weather_sunset_moderate_snow.svg) | M + P | 4.484 | 5.084 | 6.384 | 0.935 | 0.357 | 612 | 4.5 |
| [sunset / heavy_snow](backgrounds/sunset/weather_sunset_heavy_snow.svg) | M + P | 4.838 | 5.438 | 6.738 | 1.105 | 0.425 | 576 | 6 |
| [sunset / light_rain_showers](backgrounds/sunset/weather_sunset_light_rain_showers.svg) | C + D + M + P | 3.658 | 4.258 | 5.558 | 1.062 | 0.306 | 468 | 48 |
| [sunset / heavy_rain_showers](backgrounds/sunset/weather_sunset_heavy_rain_showers.svg) | M + P | 4.248 | 4.848 | 6.148 | 1.275 | 0.357 | 396 | 85 |
| [sunset / light_snow_showers](backgrounds/sunset/weather_sunset_light_snow_showers.svg) | C + D + M + P | 4.012 | 4.612 | 5.912 | 1.02 | 0.34 | 576 | 3.5 |
| [sunset / heavy_snow_showers](backgrounds/sunset/weather_sunset_heavy_snow_showers.svg) | M + P | 4.72 | 5.32 | 6.62 | 1.275 | 0.468 | 504 | 7 |
| [sunset / thunderstorm](backgrounds/sunset/weather_sunset_thunderstorm.svg) | M + P + L | 4.956 | 5.556 | 6.856 | 1.36 | 0.383 | 396 | 90 |

### Evening

| Combinazione SVG | Gruppi | T (s) | d_P (s) | t_s (s) | a_x (%W) | a_y (%H) | B (s) | v (%H/s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [evening / clear_sky](backgrounds/evening/weather_evening_clear_sky.svg) | C | — | — | 1.62 | 0.225 | 0.09 | 702 | — |
| [evening / mostly_clear](backgrounds/evening/weather_evening_mostly_clear.svg) | C + D | 3.472 | — | 3.425 | 0.675 | 0.21 | 546 | — |
| [evening / partly_cloudy](backgrounds/evening/weather_evening_partly_cloudy.svg) | C + D + M | 3.968 | — | 4.518 | 0.975 | 0.315 | 468 | — |
| [evening / overcast](backgrounds/evening/weather_evening_overcast.svg) | M | 4.34 | — | 4.89 | 0.637 | 0.165 | 624 | — |
| [evening / fog](backgrounds/evening/weather_evening_fog.svg) | M + F | 4.712 | — | 5.933 | 0.825 | 0.262 | 780 | — |
| [evening / freezing_fog](backgrounds/evening/weather_evening_freezing_fog.svg) | M + F | 5.208 | — | 6.479 | 0.6 | 0.188 | 936 | — |
| [evening / light_drizzle](backgrounds/evening/weather_evening_light_drizzle.svg) | M + P | 4.092 | 4.692 | 5.992 | 0.6 | 0.165 | 624 | 20 |
| [evening / moderate_drizzle](backgrounds/evening/weather_evening_moderate_drizzle.svg) | M + P | 4.216 | 4.816 | 6.116 | 0.675 | 0.188 | 585 | 25 |
| [evening / heavy_drizzle](backgrounds/evening/weather_evening_heavy_drizzle.svg) | M + P | 4.464 | 5.064 | 6.364 | 0.75 | 0.21 | 546 | 30 |
| [evening / light_rain](backgrounds/evening/weather_evening_light_rain.svg) | M + P | 4.092 | 4.692 | 5.992 | 0.75 | 0.21 | 546 | 45 |
| [evening / moderate_rain](backgrounds/evening/weather_evening_moderate_rain.svg) | M + P | 4.34 | 4.94 | 6.24 | 0.9 | 0.24 | 507 | 60 |
| [evening / heavy_rain](backgrounds/evening/weather_evening_heavy_rain.svg) | M + P | 4.712 | 5.312 | 6.612 | 1.05 | 0.285 | 468 | 80 |
| [evening / light_snow](backgrounds/evening/weather_evening_light_snow.svg) | M + P | 4.464 | 5.064 | 6.364 | 0.712 | 0.262 | 702 | 3 |
| [evening / moderate_snow](backgrounds/evening/weather_evening_moderate_snow.svg) | M + P | 4.712 | 5.312 | 6.612 | 0.825 | 0.315 | 663 | 4.5 |
| [evening / heavy_snow](backgrounds/evening/weather_evening_heavy_snow.svg) | M + P | 5.084 | 5.684 | 6.984 | 0.975 | 0.375 | 624 | 6 |
| [evening / light_rain_showers](backgrounds/evening/weather_evening_light_rain_showers.svg) | C + D + M + P | 3.844 | 4.444 | 5.744 | 0.938 | 0.27 | 507 | 48 |
| [evening / heavy_rain_showers](backgrounds/evening/weather_evening_heavy_rain_showers.svg) | M + P | 4.464 | 5.064 | 6.364 | 1.125 | 0.315 | 429 | 85 |
| [evening / light_snow_showers](backgrounds/evening/weather_evening_light_snow_showers.svg) | C + D + M + P | 4.216 | 4.816 | 6.116 | 0.9 | 0.3 | 624 | 3.5 |
| [evening / heavy_snow_showers](backgrounds/evening/weather_evening_heavy_snow_showers.svg) | M + P | 4.96 | 5.56 | 6.86 | 1.125 | 0.413 | 546 | 7 |
| [evening / thunderstorm](backgrounds/evening/weather_evening_thunderstorm.svg) | M + P + L | 5.208 | 5.808 | 7.108 | 1.2 | 0.338 | 429 | 90 |

### Night

| Combinazione SVG | Gruppi | T (s) | d_P (s) | t_s (s) | a_x (%W) | a_y (%H) | B (s) | v (%H/s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [night / clear_sky](backgrounds/night/weather_night_clear_sky.svg) | C | — | — | 1.62 | 0.21 | 0.084 | 756 | — |
| [night / mostly_clear](backgrounds/night/weather_night_mostly_clear.svg) | C + D | 3.64 | — | 3.576 | 0.63 | 0.196 | 588 | — |
| [night / partly_cloudy](backgrounds/night/weather_night_partly_cloudy.svg) | C + D + M | 4.16 | — | 4.71 | 0.91 | 0.294 | 504 | — |
| [night / overcast](backgrounds/night/weather_night_overcast.svg) | M | 4.55 | — | 5.1 | 0.595 | 0.154 | 672 | — |
| [night / fog](backgrounds/night/weather_night_fog.svg) | M + F | 4.94 | — | 6.184 | 0.77 | 0.245 | 840 | — |
| [night / freezing_fog](backgrounds/night/weather_night_freezing_fog.svg) | M + F | 5.46 | — | 6.756 | 0.56 | 0.175 | 1008 | — |
| [night / light_drizzle](backgrounds/night/weather_night_light_drizzle.svg) | M + P | 4.29 | 4.89 | 6.19 | 0.56 | 0.154 | 672 | 20 |
| [night / moderate_drizzle](backgrounds/night/weather_night_moderate_drizzle.svg) | M + P | 4.42 | 5.02 | 6.32 | 0.63 | 0.175 | 630 | 25 |
| [night / heavy_drizzle](backgrounds/night/weather_night_heavy_drizzle.svg) | M + P | 4.68 | 5.28 | 6.58 | 0.7 | 0.196 | 588 | 30 |
| [night / light_rain](backgrounds/night/weather_night_light_rain.svg) | M + P | 4.29 | 4.89 | 6.19 | 0.7 | 0.196 | 588 | 45 |
| [night / moderate_rain](backgrounds/night/weather_night_moderate_rain.svg) | M + P | 4.55 | 5.15 | 6.45 | 0.84 | 0.224 | 546 | 60 |
| [night / heavy_rain](backgrounds/night/weather_night_heavy_rain.svg) | M + P | 4.94 | 5.54 | 6.84 | 0.98 | 0.266 | 504 | 80 |
| [night / light_snow](backgrounds/night/weather_night_light_snow.svg) | M + P | 4.68 | 5.28 | 6.58 | 0.665 | 0.245 | 756 | 3 |
| [night / moderate_snow](backgrounds/night/weather_night_moderate_snow.svg) | M + P | 4.94 | 5.54 | 6.84 | 0.77 | 0.294 | 714 | 4.5 |
| [night / heavy_snow](backgrounds/night/weather_night_heavy_snow.svg) | M + P | 5.33 | 5.93 | 7.23 | 0.91 | 0.35 | 672 | 6 |
| [night / light_rain_showers](backgrounds/night/weather_night_light_rain_showers.svg) | C + D + M + P | 4.03 | 4.63 | 5.93 | 0.875 | 0.252 | 546 | 48 |
| [night / heavy_rain_showers](backgrounds/night/weather_night_heavy_rain_showers.svg) | M + P | 4.68 | 5.28 | 6.58 | 1.05 | 0.294 | 462 | 85 |
| [night / light_snow_showers](backgrounds/night/weather_night_light_snow_showers.svg) | C + D + M + P | 4.42 | 5.02 | 6.32 | 0.84 | 0.28 | 672 | 3.5 |
| [night / heavy_snow_showers](backgrounds/night/weather_night_heavy_snow_showers.svg) | M + P | 5.2 | 5.8 | 7.1 | 1.05 | 0.385 | 588 | 7 |
| [night / thunderstorm](backgrounds/night/weather_night_thunderstorm.svg) | M + P + L | 5.46 | 6.06 | 7.36 | 1.12 | 0.315 | 462 | 90 |

## 7. Cambio scena, durata e ritorno nella Home

- Conservare il clock della scena durante l'uso della Home; non riavviare le sinusoidi a ogni scroll. Ogni fotogramma valuta la funzione al tempo corrente: nessun accumulo numerico della deriva.
- Alla prima apertura, eseguire l'ingresso. Se l'utente lascia e riapre la Home, si può rieseguire l'ingresso preservando il tempo e le fasi del moto continuo. In quel caso usare due clock distinti: il clock dell'ingresso riparte da zero; il clock continuo riprende dal valore conservato e viene introdotto con un nuovo g da zero. Le tabelle descrivono la prima apertura.
- Sospendere il clock attivo quando la scena non è visibile; alla ripresa continuare dal valore conservato. Non simulare in un solo fotogramma tutto il tempo passato in background.
- Al cambio di condizione o fascia oraria, comporre vecchia e nuova scena in due contenitori e fare una dissolvenza di `2,5 s` con E. Durante questa transizione la nuova scena è già in posizione finale: non ripetere l'ingresso delle nuvole dall'alto. Inizializzare il suo moto con un raccordo da zero e tenere spento L fino al primo istante programmato.
- In modalità movimento ridotto: niente ingresso spaziale, niente moto autonomo o parallasse, niente lampeggi. Dissolvenza facoltativa di `0,4 s`; precipitazioni statiche.
- Una sola procedura di aggiornamento per scena visibile. Calcolare hash e riferimenti agli elementi una volta; non creare nuovi nodi né timer per ogni stella a ogni fotogramma. Per numerose particelle usare un aggiornamento aggregato o il sistema particellare del renderer.

## 8. Esempio numerico: night / heavy_rain

Dalla riga corrispondente: T=4,94 s, d_P=5,54 s, t_s=6,84 s; a_x=0,98%W, a_y=0,266%H, B=504 s e v=80%H/s. Il gruppo M entra dall'alto a t=0,45 s e termina a t=5,39 s; P sfuma da t=5,54 a 6,74 s. Da t=6,84 s parte il raccordo al moto continuo.

Per W=3840 e H=2160: l'ampiezza autonoma M è 26,3424 px in X e 4,02192 px in Y, perché p_M=0,7. L'input massimo M è 94,08 px in X e 27,216 px in Y. Per il livello più vicino, i margini minimi generali sono Rx=172,032 px e Ry=44,6256 px per lato, prima di ulteriori estensioni locali di particelle/filtri. Il primo periodo di M è 504 s; le altre componenti sono rispettivamente √2, √3 e √5 volte quel valore. La velocità media verticale delle gocce è 1728 px/s, con variazione deterministica da 1296 a 2160 px/s.

La parte lenta riguarda la composizione e la profondità; la pioggia mantiene una caduta credibile. L'effetto complessivo continua senza un riavvio globale della scena.
