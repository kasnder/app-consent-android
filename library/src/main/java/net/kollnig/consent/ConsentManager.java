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

import net.kollnig.consent.library.FacebookSdkLibrary;
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
            libraries.add(new FacebookSdkLibrary(context));
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

    public void saveConsent(String libraryId, boolean consent) {
        SharedPreferences prefs = getPreferences();

        Set<String> set = prefs.getStringSet("consents", null);
        Set<String> prefsSet = new HashSet<>();
        if (set != null)
            prefsSet.addAll(set);

        for (Library library : libraries) {
            if (!library.getId().equals(libraryId))
                continue;

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
                String libraryId = library.getId();
                Log.d(TAG, "has " + libraryId + " library, needs consent");

                int titleId = libraryId.equals("firebase_analytics") ? R.string.firebase_analytics_consent_title : R.string.facebook_sdk_consent_title;
                int messageId = libraryId.equals("firebase_analytics") ? R.string.firebase_analytics_consent_msg : R.string.facebook_sdk_consent_msg;

                Boolean consent = hasConsent(libraryId);
                if (consent == null && showConsent) {
                    final AlertDialog alertDialog = new AlertDialog.Builder(context)
                            .setTitle(titleId)
                            .setMessage(messageId)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                saveConsent(libraryId, true);
                            })
                            .setNegativeButton(R.string.no, (dialog, which) -> {
                                saveConsent(libraryId, false);
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
