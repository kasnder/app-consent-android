package net.kollnig.consent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import net.kollnig.consent.library.FirebaseAnalyticsLibrary;
import net.kollnig.consent.library.Library;
import net.kollnig.consent.library.LibraryInteractionException;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ConsentManager {
    public static final String PREFERENCES_NAME = "net.kollnig.consent";
    static final String TAG = "HOOKED";
    @SuppressLint("StaticFieldLeak")
    private static ConsentManager mConsentManager = null;
    private final Uri privacyPolicy;
    private final boolean showConsent;

    private final List<Library> libraries;

    private final Context context;

    private ConsentManager(Context context, boolean showConsent, Uri privacyPolicy) {
        this.context = context;
        this.showConsent = showConsent;
        this.privacyPolicy = privacyPolicy;

        libraries = new LinkedList<>();
        try {
            libraries.add(new FirebaseAnalyticsLibrary(context));
        } catch (LibraryInteractionException e) {
            e.printStackTrace();
        }
    }

    public static ConsentManager getInstance(Context context, Boolean showConsent, Uri privacyPolicy) {
        if (mConsentManager == null) {
            mConsentManager = new ConsentManager(context, showConsent, privacyPolicy);
            mConsentManager.initialise();
        }

        return mConsentManager;
    }

    public void saveConsent(boolean consent) {
        SharedPreferences prefs = getPreferences();

        Set<String> set = prefs.getStringSet("consents", null);
        Set<String> prefsSet = new HashSet<>();
        if (set != null)
            prefsSet.addAll(set);

        for (Library library : libraries) {
            try {
                library.saveConsent(consent);

                prefsSet.remove(library.getId() + ":" + true);
                prefsSet.remove(library.getId() + ":" + false);

                prefsSet.add(library.getId() + ":" + consent);
            } catch (LibraryInteractionException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putStringSet("consents", prefsSet).apply();
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public @Nullable
    Boolean hasConsent(String libraryId) {
        SharedPreferences prefs = getPreferences();

        Set<String> set = prefs.getStringSet("consents", new HashSet<>());
        if (set.contains(libraryId + ":" + true))
            return true;
        else if (set.contains(libraryId + ":" + false))
            return false;
        else
            return null;
    }

    private void initialise() {
        // TODO: Merge multiple libraries into one consent screen
        for (Library library: libraries) {
            if (library.isPresent()) {
                Log.d(TAG, "has " + library.getId() + " library, needs consent");

                Boolean consent = hasConsent(library.getId());
                if (consent == null && showConsent) {
                    final AlertDialog alertDialog = new AlertDialog.Builder(context)
                            .setTitle(R.string.consent_title)
                            .setMessage(R.string.consent_msg)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                saveConsent(true);
                            })
                            .setNegativeButton(R.string.no, (dialog, which) -> {
                                saveConsent(false);
                            })
                            .setNeutralButton("Privacy Policy", null)
                            .setCancelable(false)
                            .create();
                    alertDialog.setOnShowListener(dialogInterface -> {
                        Button neutralButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                        neutralButton.setOnClickListener(view -> {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, privacyPolicy);
                            context.startActivity(browserIntent);
                        });
                    });
                    alertDialog.show();
                }
            }
        }
    }
}
