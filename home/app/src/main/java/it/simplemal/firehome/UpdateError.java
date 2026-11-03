package it.simplemal.firehome;

import android.content.Context;
import java.util.Locale;

/**
 * Everything that can go wrong in an update. F is before anything changes, C during the
 * installation, R while going back. distro/tools/genera-errori.py builds ERRORI.md from this list,
 * so a new error here reaches the public page with the next release. Cause and fix are read there:
 * the TV shows the outcome, the title and a QR code to that page.
 */
enum UpdateError {
    F101("Nessuna connessione", "La Stick non è collegata a Internet.",
        "Controlla il Wi-Fi nelle Impostazioni, poi riprova."),
    F102("Server non raggiungibile", "GitHub non ha risposto.",
        "Di solito è temporaneo: riprova tra qualche minuto."),
    F103("Dati dell'aggiornamento non validi", "Le informazioni sulla nuova versione sono incomplete o illeggibili.",
        "Riprova più tardi. Se resta, segnalalo."),
    F104("Firma non valida", "Le informazioni scaricate non sono firmate con la chiave di Salotto.",
        "Non installare nulla e segnala il problema."),
    F105("Nessuna versione più recente", "La versione proposta non è più nuova di quella installata.",
        "Non serve fare nulla."),
    F106("Spazio insufficiente", "Sulla Stick non c'è spazio per scaricare e installare la nuova versione.",
        "Salotto non è stata toccata. Libera spazio togliendo le app che non usi, poi riprova, oppure aggiorna dal computer con Installa Salotto."),
    F107("Download interrotto", "Il file non è stato scaricato completamente.",
        "Controlla la connessione e riprova."),
    F108("File danneggiato", "L'impronta del file scaricato non corrisponde a quella attesa.",
        "Riprova: il file viene scaricato di nuovo. Se resta, segnalalo."),
    F109("Pacchetto illeggibile", "Il file scaricato non è un'app Android valida.",
        "Riprova. Se resta, segnalalo."),
    F110("Pacchetto sbagliato", "Il file scaricato non è Salotto.",
        "Non installare nulla e segnala il problema."),
    F111("Certificato diverso", "Il file non è firmato come la versione installata.",
        "Non installare nulla e segnala il problema."),
    F112("Versione non corrispondente", "Il file non contiene la versione annunciata.",
        "Riprova più tardi. Se resta, segnalalo."),
    F113("Stick non compatibile", "Questa versione richiede un sistema più recente.",
        "Aggiorna Fire OS dalle Impostazioni della Fire TV, poi riprova."),
    F114("Scrittura impossibile", "Salotto non riesce a salvare il file sulla Stick.",
        "Riavvia la Stick e riprova."),
    F115("Collegamento interno assente", "Il servizio che installa gli aggiornamenti non è collegato.",
        "Attendi un minuto e riprova. Se resta, controlla Impostazioni → App → Tasti tenuti premuti."),
    F116("Copia di sicurezza non riuscita", "Salotto non è riuscita a salvare le tue scelte.",
        "Riavvia la Stick e riprova."),
    F117("Video in riproduzione", "Sulla Stick si sta riproducendo qualcosa.",
        "Ferma la riproduzione e riprova."),
    C201("Installazione non avviata", "Il programma che installa non è partito.",
        "Salotto non è stata modificata. Riprova; se resta, riavvia la Stick."),
    C202("Installazione rifiutata", "Fire OS non ha accettato la nuova versione.",
        "Salotto è rimasta alla versione precedente. Riavvia la Stick e riprova."),
    C203("La nuova versione non parte", "Dopo l'installazione Salotto non si è avviata correttamente.",
        "Salotto è tornata alla versione precedente. Segnala il problema."),
    C204("Versione sbagliata dopo l'installazione", "La versione installata non è quella attesa.",
        "Salotto è tornata alla versione precedente. Segnala il problema."),
    C206("Tempo scaduto", "La nuova versione non ha confermato di funzionare in tempo.",
        "Salotto è tornata alla versione precedente. Riavvia la Stick e riprova."),
    R301("Copia precedente non disponibile", "Non è stato possibile salvare la versione installata prima di procedere.",
        "Salotto non è stata modificata. Riavvia la Stick e riprova."),
    R302("Ritorno alla versione precedente non riuscito", "Fire OS non ha accettato la versione precedente.",
        "Dal Mac lancia install.sh della distro: reinstalla Salotto senza perdere le scelte. Poi segnala il problema."),
    R303("Stato finale sconosciuto", "L'aggiornamento non ha lasciato un esito leggibile.",
        "Controlla che Salotto funzioni, poi segnala il problema."),
    R304("Serve Installa Salotto sul computer", "Su questa TV Salotto non può tornare da sola alla versione precedente.",
        "Apri Installa Salotto sul computer e scegli «Torna alla versione precedente».");

    final String title;
    final String cause;
    final String fix;

    UpdateError(String title, String cause, String fix) {
        this.title = title;
        this.cause = cause;
        this.fix = fix;
    }

    String code() { return "SAL-" + name(); }

    /** The title in the TV's language. The Italian one above stays for the public page. */
    String title(Context context) {
        String key = "update_error_" + name().toLowerCase(Locale.ROOT);
        return context.getString(context.getResources().getIdentifier(key, "string", context.getPackageName()));
    }

    /** What people need to know first: what became of Salotto. */
    String outcome(Context context) {
        switch (this) {
            case C202: return context.getString(R.string.update_outcome_stayed);
            case C203: case C204: case C206: return context.getString(R.string.update_outcome_back);
            case R302: case R304: return context.getString(R.string.update_outcome_not_back);
            case R303: return context.getString(R.string.update_outcome_unknown);
            default: return context.getString(R.string.update_outcome_untouched);
        }
    }

    /** Names written by the install script. Anything unknown is itself an error worth showing. */
    static UpdateError parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return R303;
        }
    }
}
