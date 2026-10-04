package house.indie.legacy;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Bottom sheet for menus and short forms. It replaces the Holo AlertDialog
 * lists with the same menu rows the web uses for the track menu.
 */
final class Sheet {
    private final StyledActivity activity;
    private final Dialog dialog;
    private final LinearLayout body;

    Sheet(StyledActivity activity) {
        this.activity = activity;
        dialog = new Dialog(activity, R.style.IndieSheet);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout root = activity.column();
        float radius = activity.dp(16);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.RAISED);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        root.setBackgroundDrawable(background);
        View handle = new View(activity);
        handle.setBackgroundDrawable(activity.shape(Theme.alpha(Theme.TEXT, .22f), 2));
        LinearLayout.LayoutParams handleParams = activity.size(36, 4);
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = activity.dp(10);
        handleParams.bottomMargin = activity.dp(8);
        root.addView(handle, handleParams);
        body = activity.column();
        body.setPadding(0, 0, 0, activity.dp(12));
        CappedScroll scroll = new CappedScroll(activity);
        scroll.addView(body);
        root.addView(scroll, activity.fill());
        dialog.setContentView(root);
    }

    /** Art, title and subtitle at the top of a track or playlist menu. */
    Sheet header(ArtView art, String title, String subtitle) {
        LinearLayout row = activity.row();
        row.setPadding(activity.dp(20), activity.dp(6), activity.dp(20), activity.dp(14));
        if (art != null) row.addView(art, activity.margins(activity.size(48, 48), 0, 0, 14, 0));
        LinearLayout words = activity.column();
        words.addView(activity.line(title, 16, Theme.TEXT, Theme.bold));
        if (subtitle != null && subtitle.length() > 0) words.addView(activity.line(subtitle, 13, Theme.MUTED, Theme.regular));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(row, activity.fill());
        View line = activity.divider();
        body.addView(line, activity.margins(new LinearLayout.LayoutParams(-1, 1), 0, 0, 0, 6));
        return this;
    }

    Sheet title(String title, String note) {
        LinearLayout block = activity.column();
        block.setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(12));
        block.addView(activity.text(title, 20, Theme.TEXT, Theme.extrabold));
        if (note != null) {
            TextView detail = activity.text(note, 14, Theme.MUTED, Theme.regular);
            detail.setPadding(0, activity.dp(4), 0, 0);
            block.addView(detail);
        }
        body.addView(block, activity.fill());
        return this;
    }

    Sheet item(int icon, String label, Runnable action) { return item(icon, label, null, Theme.TEXT, action); }

    Sheet item(int icon, String label, String detail, int color, final Runnable action) {
        LinearLayout row = activity.row();
        row.setPadding(activity.dp(20), 0, activity.dp(20), 0);
        row.setMinimumHeight(activity.dp(52));
        row.setBackgroundDrawable(activity.touch(0));
        row.addView(activity.glyph(icon, color == Theme.TEXT ? Theme.SOFT : color), activity.margins(activity.size(22, 22), 0, 0, 16, 0));
        LinearLayout words = activity.column();
        words.addView(activity.line(label, 15, color, Theme.regular));
        if (detail != null) words.addView(activity.line(detail, 12, Theme.MUTED, Theme.regular));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.setContentDescription(label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                dialog.dismiss();
                if (action != null) action.run();
            }
        });
        body.addView(row, activity.fill());
        return this;
    }

    Sheet view(View view) {
        body.addView(view, activity.fill());
        return this;
    }

    Sheet show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return this;
    }

    void dismiss() { if (dialog.isShowing()) dialog.dismiss(); }
    Dialog dialog() { return dialog; }

    /** A centred confirmation with a quiet cancel and one decisive action. */
    static void confirm(StyledActivity activity, String title, String message, String action,
                        boolean danger, final Runnable onConfirm) {
        final Dialog dialog = new Dialog(activity, R.style.IndieDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = activity.column();
        box.setPadding(activity.dp(22), activity.dp(22), activity.dp(22), activity.dp(16));
        box.setBackgroundDrawable(activity.outline(Theme.RAISED, Theme.LINE_SOFT, 14));
        box.addView(activity.text(title, 19, Theme.TEXT, Theme.extrabold));
        if (message != null) {
            TextView detail = activity.text(message, 14, Theme.SOFT, Theme.regular);
            detail.setLineSpacing(0, 1.15f);
            box.addView(detail, activity.margins(activity.fill(), 0, 8, 0, 0));
        }
        LinearLayout actions = activity.row();
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.addView(activity.pill(activity.str(R.string.cancel), 0, Widgets.PillButton.GHOST, new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        }), new LinearLayout.LayoutParams(-2, activity.dp(44)));
        actions.addView(activity.pill(action, 0, danger ? Widgets.PillButton.DANGER : Widgets.PillButton.PRIMARY,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { dialog.dismiss(); onConfirm.run(); }
                }), activity.margins(new LinearLayout.LayoutParams(-2, activity.dp(44)), 8, 0, 0, 0));
        box.addView(actions, activity.margins(activity.fill(), 0, 20, 0, 0));
        dialog.setContentView(box);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout(Math.min(metrics.widthPixels - activity.dp(40), activity.dp(360)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Menus never cover more than about three quarters of the screen. */
    private static final class CappedScroll extends ScrollView {
        CappedScroll(StyledActivity activity) {
            super(activity);
            setVerticalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int cap = (int) (getResources().getDisplayMetrics().heightPixels * .74f);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }
}
