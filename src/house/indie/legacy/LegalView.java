package house.indie.legacy;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The terms of use or privacy policy from `GET /legal/{document}`, the same
 * text as the library's /terms and /privacy pages, as native views. Settings
 * shows it as a page; the invitation form shows it in a sheet.
 */
final class LegalView {
    private LegalView() { }

    static LinearLayout build(final StyledActivity a, JSONObject document) {
        LinearLayout body = a.column();
        body.setPadding(a.dp(20), 0, a.dp(20), a.dp(8));
        body.addView(paragraph(a, document.optString("intro")), a.margins(a.fill(), 0, 16, 0, 0));

        JSONArray sections = document.optJSONArray("sections");
        for (int i = 0; sections != null && i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) continue;
            body.addView(heading(a, section.optString("title")), a.fill());
            JSONArray paragraphs = section.optJSONArray("paragraphs");
            for (int j = 0; paragraphs != null && j < paragraphs.length(); j++) {
                body.addView(paragraph(a, paragraphs.optString(j)), a.margins(a.fill(), 0, 0, 0, 10));
            }
            JSONArray items = section.optJSONArray("items");
            for (int j = 0; items != null && j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item != null) body.addView(item(a, item), a.margins(a.fill(), 0, 0, 0, 10));
            }
        }

        JSONObject contact = document.optJSONObject("contact");
        if (contact != null) {
            body.addView(heading(a, contact.optString("title")), a.fill());
            body.addView(paragraph(a, contact.optString("text")), a.fill());
            if (!contact.isNull("email")) {
                final String email = contact.optString("email");
                TextView link = a.text(email, 15, Theme.TEXT, Theme.bold);
                link.setPadding(0, a.dp(10), 0, a.dp(10));
                link.setBackgroundDrawable(a.touch(2));
                link.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            a.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email)));
                        } catch (ActivityNotFoundException ignored) { /* No mail app: the address stays readable. */ }
                    }
                });
                body.addView(link, a.margins(new LinearLayout.LayoutParams(-2, -2), 0, 4, 0, 0));
            }
        }
        return body;
    }

    private static TextView heading(StyledActivity a, String title) {
        TextView view = a.text(title, 19, Theme.TEXT, Theme.extrabold);
        view.setPadding(0, a.dp(18), 0, a.dp(10));
        return view;
    }

    private static TextView paragraph(StyledActivity a, String line) {
        TextView view = a.text(line, 15, Theme.SOFT, Theme.regular);
        view.setLineSpacing(0, 1.2f);
        return view;
    }

    /** A list item; privacy items name what they describe, e.g. "Account". */
    private static View item(StyledActivity a, JSONObject item) {
        LinearLayout row = a.row();
        row.setGravity(android.view.Gravity.TOP);
        View mark = new View(a);
        mark.setBackgroundDrawable(Widgets.block(a, Theme.ACCENT));
        row.addView(mark, a.margins(a.size(5, 5), 0, 9, 10, 0));
        LinearLayout words = a.column();
        if (!item.isNull("label")) words.addView(a.text(item.optString("label"), 15, Theme.TEXT, Theme.bold));
        words.addView(paragraph(a, item.optString("text")));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }
}
