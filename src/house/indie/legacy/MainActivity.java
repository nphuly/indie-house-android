package house.indie.legacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android API 18 client: every screen is an Android View, with no WebView. */
public final class MainActivity extends StyledActivity implements Player.Listener {
    static final int HOME = 0;
    static final int SEARCH = 1;
    static final int BROWSE = 2;
    static final int LIBRARY = 3;
    private static final int SCAN_REQUEST = 18;

    final ExecutorService worker = Executors.newFixedThreadPool(3);
    final Handler main = new Handler();
    IndieApi api;
    ArtworkLoader artwork;
    Player player;
    private boolean signedIn;
    /** Name, email and picture from `GET /account`; null on servers without it. */
    private JSONObject account;

    // Signed-in shell: top bar, content, mini player, bottom navigation.
    private LinearLayout topBar;
    private Widgets.LoadingStrip strip;
    private Widgets.WatchedScrollView scroll;
    private Widgets.PullRefresh pull;
    /** The page a pull-to-refresh is reloading; its spinner stays up until the fetch ends. */
    private Page pulledPage;
    LinearLayout content;
    private FrameLayout miniHost;
    private NavigationBar nav;
    private TextView toast;
    private TextView topTitle;
    private boolean topTitleShown;
    private ArtView miniArt;
    private TextView miniTitle;
    private TextView miniArtist;
    private IconButton miniPlay;
    private View miniFill;
    private LinearLayout miniBar;
    private String miniId;
    private PlayerScreen playerScreen;
    private int loading;
    private int searchSequence;
    private Bitmap stage;

    int tab;
    private final ArrayList<ArrayList<Page>> stacks = new ArrayList<ArrayList<Page>>();
    final ArrayList<TrackRow> rows = new ArrayList<TrackRow>();
    private final ArrayList<IconButton> contextButtons = new ArrayList<IconButton>();
    private final ArrayList<TextView> liveTitles = new ArrayList<TextView>();
    private final LinkedHashMap<String, JSONObject> knownTracks = new LinkedHashMap<String, JSONObject>();
    private View titleAnchor;
    Runnable nearEnd;
    LinearLayout searchBody;
    LinearLayout listBox;
    LinearLayout listFooter;

    private boolean inviteMode;
    private boolean legalOpening;
    private final Runnable hideToast = new Runnable() {
        @Override public void run() {
            if (toast != null) toast.animate().alpha(0).translationY(dp(8)).setDuration(180).withEndAction(new Runnable() {
                @Override public void run() { toast.setVisibility(View.GONE); }
            }).start();
        }
    };

    interface Done<T> { void accept(T value); }
    interface Failure { void failed(Exception error, String message); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        restoreSession();
    }

    /* ------------------------------------------------------------ Session */

    private void setApi(IndieApi value) {
        api = value;
        if (artwork != null) artwork.close();
        artwork = new ArtworkLoader(api);
        if (player != null) player.stop();
        player = new Player(getApplicationContext(), api, worker, this);
    }

    private void restoreSession() {
        showStartup(null);
        worker.execute(new Runnable() {
            @Override public void run() {
                final IndieApi restored = new IndieApi(getApplicationContext());
                try {
                    final JSONObject home;
                    if (restored.profile() != null && restored.hasSession()) {
                        restored.verify();
                        home = fetchHome(restored);
                    } else home = null;
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (isDestroyed()) return;
                            setApi(restored);
                            if (home != null) enterApp(home);
                            else showAuth(null);
                        }
                    });
                } catch (final Exception error) {
                    Log.e("IndieHouseLeap", "Session restore failed", error);
                    final boolean expired = error instanceof IndieApi.ApiError && ((IndieApi.ApiError) error).status == 401;
                    if (expired) restored.forgetSession();
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (isDestroyed()) return;
                            setApi(restored);
                            if (expired) showAuth(str(R.string.session_expired));
                            else showStartup(message(error));
                        }
                    });
                }
            }
        });
    }

    private static JSONObject fetchHome(IndieApi source) throws Exception {
        JSONObject home = new JSONObject();
        home.put("catalogue", source.request("GET", "catalogue?page=1", null));
        home.put("library", source.request("GET", "library", null).optJSONObject("data"));
        try {
            home.put("albums", source.request("GET", "albums?page=1", null).optJSONArray("data"));
        } catch (Exception ignored) {
            // Optional: servers before `GET albums` show the albums of the most played songs.
        }
        return home;
    }

    /** Cold-start screen: brand, a yellow spinner, or the problem with Retry and Change library. */
    private void showStartup(String problem) {
        clearShell();
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Theme.BG);
        LinearLayout column = column();
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(32), 0, dp(32), 0);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.brand_mark);
        column.addView(mark, size(76, 76));
        column.addView(text("INDIE HOUSE", 24, Theme.TEXT, Theme.extrabold), margins(wrap(), 0, 14, 0, 0));
        if (problem == null) {
            column.addView(Widgets.spinner(this, false), margins(size(28, 28), 0, 28, 0, 0));
            column.addView(text(str(R.string.startup_opening), 14, Theme.MUTED, Theme.regular), margins(wrap(), 0, 12, 0, 0));
        } else {
            FrameLayout badge = new FrameLayout(this);
            badge.setBackgroundDrawable(Widgets.cut(this, Widgets.Cut.PLAY, Theme.DANGER_WASH));
            badge.addView(glyph(R.drawable.ic_signal, Theme.DANGER), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            column.addView(badge, margins(size(52, 52), 0, 32, 0, 0));
            column.addView(text(str(R.string.startup_failed), 18, Theme.TEXT, Theme.extrabold), margins(wrap(), 0, 14, 0, 0));
            TextView explanation = text(problem, 14, Theme.MUTED, Theme.regular);
            explanation.setGravity(Gravity.CENTER);
            column.addView(explanation, margins(wrap(), 0, 6, 0, 0));
            column.addView(pill(str(R.string.retry), 0, Widgets.PillButton.PRIMARY, v -> restoreSession()),
                    margins(new LinearLayout.LayoutParams(-1, dp(48)), 0, 24, 0, 0));
            column.addView(pill(str(R.string.change_library), 0, Widgets.PillButton.GHOST, v -> {
                showStartup(null);
                worker.execute(() -> {
                    api.disconnect();
                    main.post(() -> { if (!isDestroyed()) { artwork.clear(); showAuth(null); } });
                });
            }), margins(new LinearLayout.LayoutParams(-1, dp(48)), 0, 8, 0, 0));
        }
        screen.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(screen);
    }

    private void showAuth(String notice) {
        signedIn = false;
        if (api.profile() == null) showConnect();
        else showLogin(notice);
    }

    private void showConnect() {
        clearShell();
        View screen = getLayoutInflater().inflate(R.layout.screen_connect, null);
        Theme.applyFonts(screen);
        setContentView(screen);
        final EditText address = (EditText) screen.findViewById(R.id.address_input);
        final Button connect = (Button) screen.findViewById(R.id.connect_button);
        final TextView error = (TextView) screen.findViewById(R.id.connect_error);
        String old = getSharedPreferences("indie_house", MODE_PRIVATE).getString("server_url", "");
        address.setText(old);
        address.setSelection(address.getText().length());
        tintField(address, R.drawable.ic_globe);
        connect.setOnClickListener(v -> discover(address.getText().toString(), connect, error));
        localeSwitch((LinearLayout) screen.findViewById(R.id.locale_switch));
        screen.findViewById(R.id.scan_button).setOnClickListener(v ->
                startActivityForResult(new Intent(MainActivity.this, QrScanActivity.class), SCAN_REQUEST));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SCAN_REQUEST || resultCode != RESULT_OK || data == null) return;
        String url = data.getStringExtra("manifest_url");
        View screen = findViewById(R.id.address_input);
        if (url == null || !(screen instanceof EditText)) return;
        ((EditText) screen).setText(url);
        discover(url, (Button) findViewById(R.id.connect_button), (TextView) findViewById(R.id.connect_error));
    }

    private void discover(final String address, final Button button, final TextView error) {
        error.setVisibility(View.GONE);
        final CharSequence label = button.getText();
        button.setEnabled(false);
        button.setText(str(R.string.connect_checking));
        async(() -> api.discover(address), candidate -> {
            button.setEnabled(true);
            button.setText(label);
            Sheet.confirm(this, str(R.string.connect_confirm_title, candidate.name),
                    str(R.string.connect_confirm_body, candidate.origin.replace("https://", "")),
                    str(R.string.connect_button), false, () -> {
                        try {
                            api.trust(candidate);
                            showLogin(null);
                        } catch (Exception failure) { showError(error, message(failure)); }
                    });
        }, (failure, text) -> {
            button.setEnabled(true);
            button.setText(label);
            showError(error, text);
        });
    }

    private void showLogin(String notice) {
        clearShell();
        View screen = getLayoutInflater().inflate(R.layout.screen_login, null);
        Theme.applyFonts(screen);
        setContentView(screen);
        ((TextView) screen.findViewById(R.id.library_name)).setText(api.profile().name);
        ((TextView) screen.findViewById(R.id.library_origin)).setText(api.profile().origin.replace("https://", ""));
        ((TextView) screen.findViewById(R.id.login_heading)).setText(inviteMode ? R.string.join_title : R.string.login_title);
        ((TextView) screen.findViewById(R.id.login_hint)).setText(inviteMode ? R.string.join_lede : R.string.login_lede);
        localeSwitch((LinearLayout) screen.findViewById(R.id.locale_switch));
        screen.findViewById(R.id.invite_fields).setVisibility(inviteMode ? View.VISIBLE : View.GONE);
        final EditText invite = (EditText) screen.findViewById(R.id.invite_input);
        final EditText name = (EditText) screen.findViewById(R.id.name_input);
        final EditText email = (EditText) screen.findViewById(R.id.email_input);
        final EditText password = (EditText) screen.findViewById(R.id.password_input);
        tintField(invite, R.drawable.ic_key);
        tintField(name, R.drawable.ic_user);
        tintField(email, R.drawable.ic_mail);
        tintField(password, R.drawable.ic_lock);
        final ImageView reveal = (ImageView) screen.findViewById(R.id.password_toggle);
        reveal.setColorFilter(Theme.MUTED);
        reveal.setOnClickListener(v -> {
            boolean hidden = (password.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
            password.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            password.setTypeface(Theme.regular);
            password.setSelection(password.getText().length());
            reveal.setImageResource(hidden ? R.drawable.ic_eye_off : R.drawable.ic_eye);
            reveal.setContentDescription(str(hidden ? R.string.hide_password : R.string.show_password));
        });
        final TextView error = (TextView) screen.findViewById(R.id.form_error);
        if (notice != null) showError(error, notice);
        final Button submit = (Button) screen.findViewById(R.id.sign_in_button);
        submit.setText(inviteMode ? R.string.join_button : R.string.login_button);
        submit.setOnClickListener(v -> signIn(invite, name, email, password, submit, error));
        Button inviteToggle = (Button) screen.findViewById(R.id.invite_toggle);
        inviteToggle.setVisibility("invite_only".equals(api.profile().mode) ? View.VISIBLE : View.GONE);
        inviteToggle.setText(inviteMode ? R.string.have_account : R.string.have_invite);
        inviteToggle.setOnClickListener(v -> { inviteMode = !inviteMode; showLogin(null); });
        if (inviteMode) {
            ViewGroup form = (ViewGroup) submit.getParent();
            form.addView(consentLine(error), form.indexOfChild(submit), margins(fill(), 0, 14, 0, 0));
        }
        screen.findViewById(R.id.change_library).setOnClickListener(v -> {
            showStartup(null);
            worker.execute(() -> {
                api.disconnect();
                main.post(() -> { if (!isDestroyed()) showConnect(); });
            });
        });
    }

    /** "By creating an account, you agree to the Terms of use…" with both documents as links, as on the web. */
    private TextView consentLine(final TextView error) {
        String terms = str(R.string.legal_terms);
        String privacy = str(R.string.legal_privacy);
        String line = str(R.string.join_consent, terms, privacy);
        SpannableString spans = new SpannableString(line);
        linkLegal(spans, line, terms, "terms", error);
        linkLegal(spans, line, privacy, "privacy", error);
        TextView view = text("", 13, Theme.MUTED, Theme.regular);
        view.setText(spans);
        view.setLinkTextColor(Theme.TEXT);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        return view;
    }

    private void linkLegal(SpannableString spans, String line, String label, final String document, final TextView error) {
        int start = line.indexOf(label);
        if (start < 0) return;
        spans.setSpan(new ClickableSpan() {
            @Override public void onClick(View view) { showLegalSheet(document, error); }
        }, start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Before sign-in there is no page stack, so the document opens in a sheet. */
    private void showLegalSheet(final String document, final TextView error) {
        if (legalOpening) return;
        legalOpening = true;
        async(() -> api.legal(document), data -> {
            legalOpening = false;
            new Sheet(this).title(data.optString("title"), data.optString("updated"))
                    .view(LegalView.build(this, data))
                    .show();
        }, (failure, text) -> {
            legalOpening = false;
            showError(error, text);
        });
    }

    private void tintField(EditText field, int icon) {
        android.graphics.drawable.Drawable glyph = Widgets.tinted(this, icon, Theme.MUTED);
        glyph.setBounds(0, 0, dp(20), dp(20));
        field.setCompoundDrawables(glyph, null, null, null);
        field.setCompoundDrawablePadding(dp(10));
        field.setTypeface(Theme.regular);
    }

    private void showError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
        Widgets.fadeIn(view, 160);
    }

    private void signIn(EditText inviteInput, EditText nameInput, EditText emailInput, final EditText passwordInput,
                        final Button submit, final TextView error) {
        final String email = emailInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        final boolean redeeming = inviteMode;
        final String invite = redeeming ? inviteInput.getText().toString().trim() : null;
        final String name = redeeming ? nameInput.getText().toString().trim() : null;
        if (redeeming && (invite.length() == 0 || name.length() == 0)) { showError(error, str(R.string.need_invite_name)); return; }
        if (email.length() == 0 || password.length() == 0) { showError(error, str(R.string.need_email_password)); return; }
        error.setVisibility(View.GONE);
        hideKeyboard();
        final CharSequence label = submit.getText();
        submit.setEnabled(false);
        submit.setText(redeeming ? R.string.joining : R.string.logging_in);
        async(() -> {
            if (redeeming) api.redeem(invite, name, email, password);
            else api.login(email, password);
            return fetchHome(api);
        }, home -> {
            passwordInput.setText("");
            inviteMode = false;
            enterApp(home);
        }, (failure, text) -> {
            submit.setEnabled(true);
            submit.setText(label);
            showError(error, text);
        });
    }

    private void enterApp(JSONObject home) {
        signedIn = true;
        stacks.clear();
        String[] roots = {Page.HOME, Page.SEARCH, Page.BROWSE, Page.LIBRARY};
        for (String kind : roots) {
            ArrayList<Page> stack = new ArrayList<Page>();
            stack.add(new Page(kind, null, NavigationBar.name(this, stacks.size())));
            stacks.add(stack);
        }
        tab = HOME;
        stacks.get(HOME).get(0).data = home;
        JSONObject catalogue = home.optJSONObject("catalogue");
        remember(catalogue == null ? null : catalogue.optJSONArray("data"));
        account = null; // Another account may have signed in; its details load below.
        buildShell();
        showPage(false);
        syncPreferences();
        syncAccount();
    }

    /** Runs a request off the main thread; a 401 while signed in returns to sign-in. */
    <T> void async(final Callable<T> task, final Done<T> done, final Failure failure) {
        setLoading(1);
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final T value = task.call();
                    main.post(new Runnable() {
                        @Override public void run() {
                            setLoading(-1);
                            if (!isDestroyed()) done.accept(value);
                        }
                    });
                } catch (final Exception error) {
                    Log.e("IndieHouseLeap", "Request failed", error);
                    main.post(new Runnable() {
                        @Override public void run() {
                            setLoading(-1);
                            if (isDestroyed()) return;
                            if (signedIn && error instanceof IndieApi.ApiError && ((IndieApi.ApiError) error).status == 401) {
                                sessionExpired();
                            } else if (failure != null) failure.failed(error, message(error));
                            else toast(message(error), true);
                        }
                    });
                }
            }
        });
    }

    private void sessionExpired() {
        if (!signedIn) return;
        signedIn = false;
        player.stop();
        artwork.clear();
        showAuth(str(R.string.session_expired));
    }

    String message(Throwable error) {
        if (error instanceof javax.net.ssl.SSLException) {
            return str(R.string.error_tls);
        }
        if (error instanceof java.net.UnknownHostException || error instanceof java.net.ConnectException
                || error instanceof java.net.SocketTimeoutException) {
            return str(R.string.error_network);
        }
        String value = error.getMessage();
        return value == null || value.length() == 0 ? str(R.string.error_generic) : value;
    }

    /* -------------------------------------------------------------- Shell */

    private void clearShell() {
        if (playerScreen != null) playerScreen.dismiss();
        topBar = null;
        strip = null;
        scroll = null;
        pull = null;
        pulledPage = null;
        content = null;
        miniHost = null;
        nav = null;
        toast = null;
        loading = 0;
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        LinearLayout shell = column();
        topBar = row();
        shell.addView(topBar, new LinearLayout.LayoutParams(-1, dp(56)));
        strip = new Widgets.LoadingStrip(this);
        shell.addView(strip, new LinearLayout.LayoutParams(-1, dp(2)));
        scroll = new Widgets.WatchedScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = column();
        content.setPadding(0, 0, 0, dp(16));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        scroll.setListener(this::onScrolled);
        pull = new Widgets.PullRefresh(this, scroll);
        pull.setListener(this::onPulled);
        shell.addView(pull, new LinearLayout.LayoutParams(-1, 0, 1));
        miniHost = new FrameLayout(this);
        shell.addView(miniHost, fill());
        nav = new NavigationBar(this, tab, this::onTabSelected);
        shell.addView(nav, fill());
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        toast = text("", 14, Theme.TEXT, Theme.semibold);
        toast.setPadding(dp(16), dp(12), dp(16), dp(12));
        toast.setGravity(Gravity.CENTER_VERTICAL);
        toast.setBackgroundDrawable(outline(Theme.HOVER, Theme.LINE, 10));
        toast.setCompoundDrawablePadding(dp(10));
        toast.setVisibility(View.GONE);
        FrameLayout.LayoutParams toastParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        toastParams.setMargins(dp(12), 0, dp(12), dp(70));
        root.addView(toast, toastParams);
        setContentView(root);
        buildMini();
        updateMini();
    }

    private void buildTopBar(Page page) {
        topBar.removeAllViews();
        topTitle = null;
        topTitleShown = false;
        int color = page.isRoot() || page.tint == 0 ? Theme.BG : page.tint;
        topBar.setBackgroundColor(color);
        strip.setBackgroundColor(color);
        if (page.isRoot()) {
            topBar.setPadding(dp(16), 0, dp(12), 0);
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.brand_mark);
            topBar.addView(mark, size(32, 32));
            topBar.addView(text("INDIE HOUSE", 16, Theme.TEXT, Theme.extrabold), margins(new LinearLayout.LayoutParams(0, -2, 1), 8, 0, 0, 0));
            String picture = account == null || account.isNull("avatar_url") ? null : account.optString("avatar_url");
            FrameLayout avatar;
            if (picture != null) {
                // The profile picture uploaded on the web, in the same octagon.
                ArtView art = new ArtView(this, 4);
                art.setSquircle(true);
                art.bind(account.optString("name"), account.optString("name"), picture, artwork);
                avatar = art;
            } else {
                avatar = new FrameLayout(this);
                avatar.setBackgroundDrawable(Widgets.states(Widgets.cut(this, Widgets.Cut.OCTAGON, Theme.PINK),
                        Widgets.cut(this, Widgets.Cut.OCTAGON, Theme.mix(Theme.PINK, 0xffffffff, .25f))));
                avatar.addView(glyph(R.drawable.ic_user, Theme.INK), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            }
            avatar.setContentDescription(str(R.string.account_settings));
            avatar.setOnClickListener(v -> open(new Page(Page.SETTINGS, null, str(R.string.settings))));
            topBar.addView(avatar, size(36, 36));
        } else {
            topBar.setPadding(dp(4), 0, dp(16), 0);
            topBar.addView(iconButton(R.drawable.ic_back, IconButton.PLAIN, str(R.string.back), v -> back()), size(48, 48));
            topTitle = line(page.title == null ? "" : page.title, 16, Theme.TEXT, Theme.bold);
            topTitle.setAlpha(0);
            topBar.addView(topTitle, margins(new LinearLayout.LayoutParams(0, -2, 1), 6, 0, 0, 0));
        }
    }

    void setHeaderTint(Page page, int tint) {
        if (topBar == null || page != current() || page.isRoot()) return;
        topBar.setBackgroundColor(tint);
        strip.setBackgroundColor(tint);
    }

    void setTitleAnchor(View view) { titleAnchor = view; }

    private void onScrolled(int y) {
        Page page = current();
        if (page != null) page.scroll = y;
        updateTopTitle();
        if (nearEnd != null && y + scroll.getHeight() > content.getHeight() - dp(640)) {
            Runnable load = nearEnd;
            nearEnd = null;
            load.run();
        }
    }

    /** Detail titles fade into the top bar once the hero title scrolls under it, like the web sticky head. */
    private void updateTopTitle() {
        if (topTitle == null || titleAnchor == null) return;
        int[] anchor = new int[2];
        int[] bar = new int[2];
        titleAnchor.getLocationOnScreen(anchor);
        topBar.getLocationOnScreen(bar);
        boolean show = anchor[1] + titleAnchor.getHeight() / 2 < bar[1] + topBar.getHeight();
        if (show == topTitleShown) return;
        topTitleShown = show;
        topTitle.animate().alpha(show ? 1 : 0).setDuration(160).start();
    }

    private void setLoading(int delta) {
        loading = Math.max(0, loading + delta);
        if (strip != null) strip.setActive(loading > 0);
    }

    void toast(String message) { toast(message, false); }

    void toast(String message, boolean error) {
        if (toast == null) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); return; }
        android.graphics.drawable.Drawable icon = Widgets.tinted(this, error ? R.drawable.ic_alert : R.drawable.ic_check,
                error ? Theme.DANGER : Theme.ACCENT);
        icon.setBounds(0, 0, dp(18), dp(18));
        toast.setCompoundDrawables(icon, null, null, null);
        toast.setText(message);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) toast.getLayoutParams();
        params.bottomMargin = nav.getHeight() + (miniHost.getVisibility() == View.VISIBLE ? miniHost.getHeight() : 0) + dp(10);
        toast.setLayoutParams(params);
        main.removeCallbacks(hideToast);
        toast.animate().cancel();
        toast.setVisibility(View.VISIBLE);
        toast.setAlpha(0);
        toast.setTranslationY(dp(8));
        toast.animate().alpha(1).translationY(0).setDuration(180).start();
        main.postDelayed(hideToast, error ? 4200 : 2600);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    /* --------------------------------------------------------- Navigation */

    Page current() {
        if (stacks.isEmpty()) return null;
        ArrayList<Page> stack = stacks.get(tab);
        return stack.get(stack.size() - 1);
    }

    void showPage(boolean restoreScroll) {
        final Page page = current();
        if (page == null || content == null) return;
        rows.clear();
        contextButtons.clear();
        liveTitles.clear();
        titleAnchor = null;
        nearEnd = null;
        searchBody = null;
        listBox = null;
        listFooter = null;
        hideKeyboard();
        content.removeAllViews();
        buildTopBar(page);
        nav.select(tab);
        new Screens(this).build(page);
        refreshNowPlaying();
        pull.setPullable(page.needsData());
        if (pulledPage != null && pulledPage != page) endPull(pulledPage);
        final int y = restoreScroll ? page.scroll : 0;
        page.scroll = y;
        scroll.scrollTo(0, 0);
        scroll.post(() -> {
            if (y > 0) scroll.scrollTo(0, y);
            updateTopTitle();
        });
        if (page.needsData() && !page.loading && (page.stale || page.data == null && page.error == null)) fetch(page);
    }

    void open(Page page) {
        if (playerScreen != null) playerScreen.dismiss();
        Page from = current();
        if (from != null) from.scroll = scroll.getScrollY();
        stacks.get(tab).add(page);
        showPage(false);
    }

    void back() {
        ArrayList<Page> stack = stacks.get(tab);
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            showPage(true);
        } else if (tab != HOME) selectTab(HOME);
    }

    void selectTab(int target) { onTabSelected(target, target == tab); }

    private void onTabSelected(int target, boolean again) {
        if (playerScreen != null) playerScreen.dismiss();
        Page from = current();
        if (!again) {
            if (from != null) from.scroll = scroll.getScrollY();
            tab = target;
            showPage(true);
            return;
        }
        // Tapping the active tab returns to its first page, then to the top, then refreshes.
        ArrayList<Page> stack = stacks.get(tab);
        if (stack.size() > 1) {
            while (stack.size() > 1) stack.remove(stack.size() - 1);
            stack.get(0).scroll = 0;
            showPage(true);
        } else if (scroll.getScrollY() > 0) scroll.smoothScrollTo(0, 0);
        else if (stack.get(0).needsData()) load(stack.get(0));
    }

    @Override public void onBackPressed() {
        if (!signedIn || stacks.isEmpty()) { super.onBackPressed(); return; }
        if (stacks.get(tab).size() > 1 || tab != HOME) { back(); return; }
        // Keep the music going: leave like the home button while something is loaded.
        if (player != null && player.current() != null) moveTaskToBack(true);
        else super.onBackPressed();
    }

    /** Shows a retry, or refreshes cached data in the background. */
    void load(Page page) {
        page.error = null;
        page.stale = true;
        if (page == current()) showPage(true);
    }

    private void fetch(final Page page) {
        page.loading = true;
        async(fetcher(page), data -> {
            page.loading = false;
            page.stale = false;
            page.error = null;
            apply(page, data);
            if (page == current()) showPage(true);
            endPull(page);
        }, (error, text) -> {
            page.loading = false;
            page.stale = false;
            if (page.data == null) page.error = text;
            else toast(text, true);
            if (page == current()) showPage(true);
            endPull(page);
        });
    }

    /** Pulled down at the top of the page: reload it, keeping what is shown until the new data arrives. */
    private void onPulled() {
        Page page = current();
        if (page == null || !page.needsData()) { pull.setRefreshing(false); return; }
        pulledPage = page;
        if (page.loading) return; // The fetch in flight ends the pull.
        if (page.data == null) {
            // Only an error is shown: rebuild with the skeleton, as Retry does.
            load(page);
            return;
        }
        page.stale = true;
        fetch(page);
    }

    private void endPull(Page page) {
        if (page != pulledPage) return;
        pulledPage = null;
        if (pull != null) pull.setRefreshing(false);
    }

    private Callable<JSONObject> fetcher(final Page page) {
        final String kind = page.kind;
        final String id = page.id;
        return () -> {
            if (Page.HOME.equals(kind)) return fetchHome(api);
            if (Page.BROWSE.equals(kind)) return api.request("GET", "browse", null).getJSONObject("data");
            if (page.isList()) return api.request("GET", kind + "?page=1", null);
            if (Page.LIBRARY.equals(kind) || Page.HISTORY.equals(kind)) return api.request("GET", "library", null).getJSONObject("data");
            if (Page.SETTINGS.equals(kind)) {
                JSONObject result = new JSONObject();
                result.put("preferences", api.request("GET", "preferences", null).optJSONObject("data"));
                result.put("devices", api.request("GET", "devices", null).optJSONArray("data"));
                try {
                    result.put("account", api.request("GET", "account", null).optJSONObject("data"));
                } catch (IndieApi.ApiError error) {
                    if (error.status != 404) throw error; // Older servers have no /account.
                }
                return result;
            }
            if (Page.LEGAL.equals(kind)) return api.legal(id);
            if (id == null || !id.matches("[0-9a-zA-Z-]{1,64}")) throw new IndieApi.ApiError(str(R.string.error_not_found), 404);
            return api.request("GET", kind + "s/" + id, null).getJSONObject("data");
        };
    }

    private void apply(Page page, JSONObject data) {
        if (page.isList()) {
            page.items.clear();
            page.listPage = 0;
            appendList(page, data);
            page.data = data;
            return;
        }
        page.data = data;
        if (Page.SETTINGS.equals(page.kind)) setAccount(data.optJSONObject("account"));
        if (Page.HOME.equals(page.kind)) {
            JSONObject catalogue = data.optJSONObject("catalogue");
            remember(catalogue == null ? null : catalogue.optJSONArray("data"));
        } else if (Page.BROWSE.equals(page.kind)) remember(data.optJSONArray("new_tracks"));
        else remember(data.optJSONArray("tracks"));
        if (page.title == null || page.title.length() == 0) page.title = data.optString(Page.ARTIST.equals(page.kind) ? "name" : "title");
    }

    /** Adds a page of albums or artists, dropping any already shown. */
    private void appendList(Page page, JSONObject response) {
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (JSONObject item : page.items) seen.add(item.optString("id"));
        for (JSONObject item : Screens.list(response.optJSONArray("data"))) if (seen.add(item.optString("id"))) page.items.add(item);
        JSONObject meta = response.optJSONObject("meta");
        page.listPage = meta == null ? page.listPage + 1 : meta.optInt("current_page", page.listPage + 1);
        page.listLastPage = meta == null ? page.listPage : meta.optInt("last_page", page.listPage);
        page.listTotal = meta == null ? page.items.size() : meta.optInt("total", page.items.size());
    }

    void loadMore(final Page page) {
        if (page.loadingMore || page.listPage >= page.listLastPage) return;
        page.loadingMore = true;
        if (page == current() && listFooter != null) new Screens(this).listFooter(page);
        final int next = page.listPage + 1;
        async(() -> api.request("GET", page.kind + "?page=" + next, null), response -> {
            page.loadingMore = false;
            int from = page.items.size();
            appendList(page, response);
            if (page == current() && listBox != null) {
                Screens screens = new Screens(this);
                screens.appendListItems(page, from);
                screens.listFooter(page);
            }
        }, (error, text) -> {
            page.loadingMore = false;
            toast(text, true);
            if (page == current() && listFooter != null) {
                new Screens(this).listFooter(page);
                nearEnd = null; // Retry only from the button after a failure.
            }
        });
    }

    /** Marks library-derived pages for a quiet refresh the next time they show. */
    private void libraryChanged() {
        for (ArrayList<Page> stack : stacks) for (Page page : stack) {
            if (Page.HOME.equals(page.kind) || Page.LIBRARY.equals(page.kind) || Page.HISTORY.equals(page.kind)
                    || Page.PLAYLIST.equals(page.kind)) page.stale = true;
        }
    }

    /* ---------------------------------------------------------- Now playing */

    void registerRow(TrackRow row) { rows.add(row); }

    void registerContextButton(IconButton button, String context, String label) {
        button.setTag(new String[] {context, label});
        contextButtons.add(button);
    }

    void registerLive(TextView title, String context) {
        title.setTag(context);
        liveTitles.add(title);
    }

    /** Marks the current song and the playing album, playlist or list, like `syncNowPlaying` on the web. */
    void refreshNowPlaying() {
        if (player == null) return;
        String id = player.currentId();
        boolean playing = player.isPlaying() || player.isLoading();
        String context = player.current() == null ? null : player.contextId();
        for (TrackRow row : rows) row.setState(id != null && id.equals(row.trackId()), player.isPlaying());
        for (IconButton button : contextButtons) {
            String[] tag = (String[]) button.getTag();
            button.setPlaying(context != null && context.equals(tag[0]) && playing, tag[1]);
        }
        for (TextView title : liveTitles) title.setTextColor(context != null && context.equals(title.getTag()) ? Theme.ACCENT : Theme.TEXT);
    }

    void playList(List<JSONObject> tracks, int index, String context, String label, boolean shuffled) {
        if (tracks.isEmpty()) return;
        player.start(tracks, index, context, label, shuffled);
    }

    /** A page's play button pauses or resumes its own list and starts it otherwise. */
    void playContext(List<JSONObject> tracks, String context, String label) {
        if (player.current() != null && context.equals(player.contextId())) player.toggle();
        else playList(new ArrayList<JSONObject>(tracks), 0, context, label, player.shuffle());
    }

    @Override public void onPlayerChanged() {
        updateMini();
        refreshNowPlaying();
        if (playerScreen != null) playerScreen.update();
    }

    @Override public void onPlayerProgress() {
        updateMiniProgress();
        if (playerScreen != null) playerScreen.progress();
    }

    @Override public void onPlayerError(String message) { toast(message, true); }
    @Override public void onPlayerNotice(String message) { toast(message); }

    void onPlayerClosed(PlayerScreen screen) { if (playerScreen == screen) playerScreen = null; }

    private void openPlayer() {
        if (player.current() == null || playerScreen != null) return;
        playerScreen = new PlayerScreen(this);
    }

    private void buildMini() {
        LinearLayout box = column();
        miniBar = row();
        miniBar.setPadding(dp(8), dp(8), dp(4), dp(8));
        miniArt = new ArtView(this, 4);
        miniBar.addView(miniArt, size(40, 40));
        LinearLayout words = column();
        words.setPadding(dp(10), 0, dp(4), 0);
        miniTitle = line("", 14, Theme.TEXT, Theme.bold);
        words.addView(miniTitle);
        miniArtist = line("", 12, Theme.SOFT, Theme.regular);
        words.addView(miniArtist);
        miniBar.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        miniPlay = iconButton(R.drawable.ic_play, IconButton.PLAIN, str(R.string.play), v -> player.toggle());
        miniPlay.iconSize(24);
        miniBar.addView(miniPlay, size(48, 48));
        box.addView(miniBar, fill());
        FrameLayout track = new FrameLayout(this);
        track.setBackgroundColor(Theme.alpha(Theme.TEXT, .14f));
        miniFill = new View(this);
        miniFill.setBackgroundColor(Theme.ACCENT);
        miniFill.setPivotX(0);
        miniFill.setScaleX(0);
        track.addView(miniFill, new FrameLayout.LayoutParams(-1, -1));
        box.addView(track, margins(new LinearLayout.LayoutParams(-1, dp(2)), 8, 0, 8, 0));
        box.setBackgroundDrawable(shape(Theme.RAISED, 8));
        box.setOnClickListener(v -> openPlayer());
        box.setContentDescription(str(R.string.open_player));
        miniArt.setListener(bitmap -> box.setBackgroundDrawable(shape(Theme.mix(Theme.ambient(bitmap), Theme.RAISED, .45f), 8)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2);
        params.setMargins(dp(8), dp(2), dp(8), dp(6));
        miniHost.addView(box, params);
    }

    private void updateMini() {
        if (miniHost == null) return;
        JSONObject track = player.current();
        miniHost.setVisibility(track == null ? View.GONE : View.VISIBLE);
        if (track == null) { miniId = null; return; }
        String id = track.optString("id");
        View box = (View) miniBar.getParent();
        if (!id.equals(miniId)) {
            miniId = id;
            String title = track.optString("title", str(R.string.untitled));
            box.setBackgroundDrawable(shape(Theme.RAISED, 8));
            miniArt.bind(title, title, Screens.url(track, "artwork_url"), artwork);
            miniTitle.setText(title);
        }
        boolean failed = player.hasFailed();
        miniArtist.setText(failed ? str(R.string.mini_failed) : artistName(track));
        miniArtist.setTextColor(failed ? Theme.DANGER : Theme.SOFT);
        miniPlay.setBusy(player.isLoading());
        boolean playing = player.isPlaying();
        miniPlay.setIcon(playing ? R.drawable.ic_pause : R.drawable.ic_play, str(playing ? R.string.pause : R.string.play));
        updateMiniProgress();
    }

    private void updateMiniProgress() {
        if (miniFill == null) return;
        int duration = player.duration();
        miniFill.setScaleX(duration <= 0 ? 0 : Math.min(1f, player.position() / (float) duration));
    }

    /* ------------------------------------------------------------ Actions */

    void trackMenu(final JSONObject track, final String playlistId) {
        final String title = track.optString("title", str(R.string.untitled));
        ArtView art = new ArtView(this, 4);
        art.bind(title, title, Screens.url(track, "artwork_url"), artwork);
        Sheet sheet = new Sheet(this).header(art, title, artistName(track));
        sheet.item(R.drawable.ic_plus, str(R.string.add_to_playlist), () -> addToPlaylist(track));
        sheet.item(R.drawable.ic_queue, str(R.string.play_next), () -> {
            player.playNext(track);
            toast(str(R.string.will_play_next, title));
        });
        JSONArray albums = track.optJSONArray("albums");
        final JSONObject album = albums == null || albums.length() == 0 ? null : albums.optJSONObject(0);
        if (album != null) sheet.item(R.drawable.ic_disc, str(R.string.go_to_album), album.optString("title"), Theme.TEXT,
                () -> open(new Page(Page.ALBUM, album.optString("id"), album.optString("title"))));
        final JSONObject artist = track.optJSONObject("artist");
        if (artist != null) sheet.item(R.drawable.ic_user, str(R.string.go_to_artist), artist.optString("name"), Theme.TEXT,
                () -> open(new Page(Page.ARTIST, artist.optString("id"), artist.optString("name"))));
        if (playlistId != null) sheet.item(R.drawable.ic_trash, str(R.string.remove_from_playlist), null, Theme.DANGER,
                () -> removeFromPlaylist(playlistId, track));
        sheet.show();
    }

    void addToPlaylist(final JSONObject track) {
        async(() -> api.request("GET", "playlists", null).getJSONArray("data"), playlists -> {
            Sheet sheet = new Sheet(this).title(str(R.string.add_to_playlist), track.optString("title"));
            sheet.item(R.drawable.ic_plus, str(R.string.new_playlist), null, Theme.ACCENT, () -> createPlaylist(track));
            for (final JSONObject playlist : Screens.list(playlists)) {
                boolean isPublic = playlist.optBoolean("is_public");
                sheet.item(isPublic ? R.drawable.ic_library : R.drawable.ic_lock, playlist.optString("title"),
                        str(R.string.pair, plural(R.plurals.songs, playlist.optInt("tracks_count")), str(isPublic ? R.string.public_label : R.string.private_label)), Theme.TEXT,
                        () -> addTrack(playlist.optString("id"), playlist.optString("title"), track, null));
            }
            sheet.show();
        }, null);
    }

    private void addTrack(final String playlistId, final String playlistTitle, final JSONObject track, final Runnable after) {
        if (!playlistId.matches("[0-9a-zA-Z-]{1,64}")) return;
        async(() -> {
            JSONObject payload = new JSONObject();
            payload.put("track_id", track.getString("id"));
            return api.request("POST", "playlists/" + playlistId + "/tracks", payload);
        }, value -> {
            libraryChanged();
            toast(str(R.string.added_to, playlistTitle));
            if (after != null) after.run();
        }, null);
    }

    private void removeFromPlaylist(final String playlistId, final JSONObject track) {
        final String trackId = track.optString("id");
        if (!playlistId.matches("[0-9a-zA-Z-]{1,64}") || !trackId.matches("[0-9a-zA-Z-]{1,64}")) return;
        async(() -> api.request("DELETE", "playlists/" + playlistId + "/tracks/" + trackId, null), value -> {
            libraryChanged();
            Page page = current();
            if (page != null && Page.PLAYLIST.equals(page.kind) && playlistId.equals(page.id) && page.data != null) {
                JSONArray kept = new JSONArray();
                for (JSONObject item : Screens.list(page.data.optJSONArray("tracks"))) if (!trackId.equals(item.optString("id"))) kept.put(item);
                try { page.data.put("tracks", kept); } catch (org.json.JSONException ignored) { }
                page.stale = false;
                showPage(true);
            }
            toast(str(R.string.removed_from_playlist));
        }, null);
    }

    void createPlaylist(final JSONObject track) { playlistForm(null, track); }

    void editPlaylist(Page page) { if (page.data != null) playlistForm(page, null); }

    /** Create and edit share one sheet: name, description and a public switch, as the web popover. */
    private void playlistForm(final Page page, final JSONObject track) {
        final boolean editing = page != null;
        final Sheet sheet = new Sheet(this).title(str(editing ? R.string.edit_playlist : R.string.create_playlist),
                editing ? null : str(R.string.create_playlist_note));
        LinearLayout form = column();
        form.setPadding(dp(20), dp(4), dp(20), dp(8));
        form.addView(fieldLabel(str(R.string.playlist_name)));
        final EditText name = input(str(R.string.playlist_name_hint), 0, false);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
        form.addView(name, new LinearLayout.LayoutParams(-1, dp(50)));
        form.addView(fieldLabel(str(R.string.description)), margins(fill(), 0, 16, 0, 0));
        final EditText description = input(str(R.string.optional), 0, false);
        description.setSingleLine(false);
        description.setMinLines(2);
        description.setMaxLines(4);
        description.setGravity(Gravity.TOP);
        description.setPadding(dp(16), dp(12), dp(16), dp(12));
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        form.addView(description, fill());
        LinearLayout visibility = row();
        LinearLayout words = column();
        words.addView(text(str(R.string.public_label), 15, Theme.TEXT, Theme.bold));
        words.addView(text(str(R.string.public_hint), 13, Theme.MUTED, Theme.regular));
        visibility.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        final Widgets.Toggle shared = new Widgets.Toggle(this);
        shared.setContentDescription(str(R.string.public_label));
        visibility.addView(shared, margins(size(44, 28), 12, 0, 0, 0));
        form.addView(visibility, margins(fill(), 0, 18, 0, 0));
        final TextView error = text("", 13, Theme.DANGER, Theme.regular);
        error.setVisibility(View.GONE);
        form.addView(error, margins(fill(), 0, 12, 0, 0));
        if (editing) {
            name.setText(page.data.optString("title"));
            String old = page.data.optString("description");
            description.setText("null".equals(old) ? "" : old);
            shared.setChecked(page.data.optBoolean("is_public"), false);
        }
        final Widgets.PillButton[] submit = new Widgets.PillButton[1];
        submit[0] = pill(str(editing ? R.string.save_changes : track != null ? R.string.create_and_add : R.string.create_playlist), 0, Widgets.PillButton.PRIMARY, v -> {
            final String title = name.getText().toString().trim();
            if (title.length() < 2) { showError(error, str(R.string.name_too_short)); return; }
            error.setVisibility(View.GONE);
            submit[0].setBusy(true, str(editing ? R.string.saving : R.string.creating));
            final String note = description.getText().toString().trim();
            final boolean isPublic = shared.isChecked();
            async(() -> {
                JSONObject payload = new JSONObject();
                payload.put("title", title);
                payload.put("description", note.length() == 0 ? JSONObject.NULL : note);
                payload.put("is_public", isPublic);
                if (editing) return api.request("PATCH", "playlists/" + page.id, payload).getJSONObject("data");
                return api.request("POST", "playlists", payload).getJSONObject("data");
            }, saved -> {
                sheet.dismiss();
                libraryChanged();
                if (editing) {
                    try {
                        page.data.put("title", saved.optString("title"));
                        page.data.put("description", saved.opt("description"));
                        page.data.put("is_public", saved.optBoolean("is_public"));
                    } catch (org.json.JSONException ignored) { }
                    page.title = saved.optString("title");
                    page.stale = false;
                    if (page == current()) showPage(true);
                    toast(str(R.string.playlist_saved));
                } else if (track != null) {
                    addTrack(saved.optString("id"), saved.optString("title"), track, null);
                } else {
                    toast(str(R.string.playlist_created, saved.optString("title")));
                    Page shown = current();
                    if (shown != null && Page.LIBRARY.equals(shown.kind)) load(shown);
                }
            }, (failure, text) -> {
                submit[0].setBusy(false, null);
                showError(error, text);
            });
        });
        form.addView(submit[0], margins(new LinearLayout.LayoutParams(-1, dp(48)), 0, 16, 0, 0));
        form.addView(pill(str(R.string.cancel), 0, Widgets.PillButton.GHOST, v -> sheet.dismiss()), margins(new LinearLayout.LayoutParams(-1, dp(44)), 0, 6, 0, 0));
        sheet.view(form).show();
        name.requestFocus();
        if (sheet.dialog().getWindow() != null) {
            sheet.dialog().getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    void deletePlaylist(final Page page) {
        Sheet.confirm(this, str(R.string.delete_playlist_title), str(R.string.delete_playlist_body, page.title),
                str(R.string.delete_playlist), true, () -> async(() -> api.request("DELETE", "playlists/" + page.id, null), value -> {
                    libraryChanged();
                    ArrayList<Page> stack = stacks.get(tab);
                    stack.remove(page);
                    if (stack.isEmpty()) stack.add(new Page(Page.LIBRARY, null, str(R.string.nav_library)));
                    showPage(true);
                    toast(str(R.string.playlist_deleted));
                }, null));
    }

    void revokeDevice(final JSONObject device) {
        final String id = device.optString("id");
        if (!id.matches("[0-9a-fA-F-]{36}")) return;
        Sheet.confirm(this, str(R.string.revoke_title), str(R.string.revoke_body), str(R.string.revoke), true, () -> async(() -> api.request("DELETE", "devices/" + id, null), value -> {
                    toast(str(R.string.device_revoked));
                    Page page = current();
                    if (page != null && Page.SETTINGS.equals(page.kind)) load(page);
                }, null));
    }

    /** Saves one preference; the page shows the new value at once and reverts if the save fails. */
    void savePreference(final String key, final Object value) {
        final Page page = current();
        final JSONObject preferences = page == null || page.data == null ? null : page.data.optJSONObject("preferences");
        final Object previous = preferences == null ? null : preferences.opt(key);
        try { if (preferences != null) preferences.put(key, value); } catch (org.json.JSONException ignored) { }
        if ("audio_quality".equals(key) && page != null) showPage(true);
        async(() -> {
            JSONObject payload = new JSONObject();
            payload.put(key, value);
            return api.request("PATCH", "preferences", payload).getJSONObject("data");
        }, saved -> {
            applyPreferences(saved);
            if (page != null && page.data != null) {
                try { page.data.put("preferences", saved); } catch (org.json.JSONException ignored) { }
            }
            toast(str(R.string.saved));
        }, (failure, text) -> {
            try { if (preferences != null) preferences.put(key, previous); } catch (org.json.JSONException ignored) { }
            if (page == current()) showPage(true);
            toast(text, true);
        });
    }

    private void applyPreferences(JSONObject preferences) {
        if (preferences == null) return;
        api.setQuality(preferences.optString("audio_quality", "auto"));
        player.setAutoplay(preferences.optBoolean("autoplay", true));
        if (playerScreen != null) playerScreen.update();
    }

    private void syncPreferences() {
        async(() -> api.request("GET", "preferences", null).optJSONObject("data"), this::applyPreferences, (error, text) -> { });
    }

    private void syncAccount() {
        async(() -> api.request("GET", "account", null).optJSONObject("data"), this::setAccount, (error, text) -> { });
    }

    /** Keeps the name and picture, and redraws the top bar's account button on a tab's first page. */
    private void setAccount(JSONObject value) {
        if (value == null) return;
        account = value;
        Page page = current();
        if (topBar != null && page != null && page.isRoot()) buildTopBar(page);
    }

    /* ----------------------------------------------------------- Language */

    /** A small VI | EN toggle for the connect and sign-in screens. */
    private void localeSwitch(LinearLayout host) {
        if (host == null) return;
        host.removeAllViews();
        host.setBackgroundDrawable(outline(0, Theme.LINE, 0));
        String current = Locales.current(this);
        String[][] options = {{Locales.VI, "VI", str(R.string.language_vi)}, {Locales.EN, "EN", str(R.string.language_en)}};
        for (final String[] option : options) {
            boolean active = option[0].equals(current);
            TextView button = text(option[1], 12, active ? Theme.INK : Theme.MUTED, Theme.mono);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundDrawable(active ? shape(Theme.TEXT, 0) : touch(0));
            button.setContentDescription(active ? str(R.string.selected_suffix, option[2]) : option[2]);
            button.setOnClickListener(v -> setLanguage(option[0]));
            host.addView(button, new LinearLayout.LayoutParams(dp(38), -1));
        }
    }

    /**
     * Switches the UI language in place. The player and its queue live on
     * this activity, so rebuilding the views keeps music playing.
     */
    void setLanguage(String code) {
        if (code.equals(Locales.current(this))) return;
        Locales.save(this, code);
        Locales.apply(this);
        if (!signedIn) {
            if (api == null || api.profile() == null) showConnect();
            else showLogin(null);
            return;
        }
        Page page = current();
        if (page != null && scroll != null) page.scroll = scroll.getScrollY();
        for (int i = 0; i < stacks.size(); i++) stacks.get(i).get(0).title = NavigationBar.name(this, i);
        clearShell();
        buildShell();
        showPage(true);
    }

    void signOut() {
        Sheet.confirm(this, str(R.string.logout_title), str(R.string.logout_body), str(R.string.logout), true, () -> {
                    signedIn = false;
                    player.stop();
                    artwork.clear();
                    worker.execute(() -> api.logout());
                    showLogin(null);
                });
    }

    void changeLibrary() {
        Sheet.confirm(this, str(R.string.change_library_title), str(R.string.change_library_body, api.profile().name),
                str(R.string.change_library), true, () -> {
                    signedIn = false;
                    player.stop();
                    artwork.clear();
                    showStartup(null);
                    worker.execute(() -> {
                        api.disconnect();
                        main.post(() -> { if (!isDestroyed()) showConnect(); });
                    });
                });
    }

    /* ------------------------------------------------------------- Search */

    void runSearch(final Page page, boolean force) {
        final String q = page.query.trim();
        if (q.length() == 0) return;
        if (!force && q.equals(page.resultsFor) && page.results != null) return;
        page.searching = true;
        page.searchError = null;
        if (page.results == null && searchBody != null && page == current()) new Screens(this).searchBody(page, searchBody);
        final int sequence = ++searchSequence;
        async(() -> api.request("GET", "search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), null).getJSONObject("data"), data -> {
            if (sequence != searchSequence) return;
            page.searching = false;
            page.results = data;
            page.resultsFor = q;
            page.filter = "all";
            remember(data.optJSONArray("tracks"));
            if (page == current() && searchBody != null) new Screens(this).searchBody(page, searchBody);
        }, (error, text) -> {
            if (sequence != searchSequence) return;
            page.searching = false;
            if (page.results == null) {
                page.searchError = text;
                if (page == current() && searchBody != null) new Screens(this).searchBody(page, searchBody);
            } else toast(text, true);
        });
    }

    void setSearchQuery(Page page, String query) {
        page.query = query;
        page.results = null;
        page.resultsFor = null;
        rememberSearch(query);
        showPage(false);
        runSearch(page, true);
    }

    List<String> recentSearches() {
        ArrayList<String> result = new ArrayList<String>();
        try {
            JSONArray saved = new JSONArray(prefs().getString("recent_searches", "[]"));
            for (int i = 0; i < saved.length(); i++) result.add(saved.getString(i));
        } catch (org.json.JSONException ignored) { /* Start over with an empty list. */ }
        return result;
    }

    void rememberSearch(String query) {
        JSONArray next = new JSONArray();
        next.put(query);
        for (String old : recentSearches()) if (!old.equalsIgnoreCase(query) && next.length() < 8) next.put(old);
        prefs().edit().putString("recent_searches", next.toString()).apply();
    }

    void clearRecentSearches() { prefs().edit().remove("recent_searches").apply(); }

    List<Map.Entry<String, Integer>> knownGenres() { return Screens.genres(new ArrayList<JSONObject>(knownTracks.values())); }

    private void remember(JSONArray tracks) {
        for (JSONObject track : Screens.list(tracks)) knownTracks.put(track.optString("id"), track);
        while (knownTracks.size() > 600) knownTracks.remove(knownTracks.keySet().iterator().next());
    }

    private SharedPreferences prefs() { return getSharedPreferences("indie_house", MODE_PRIVATE); }

    /* -------------------------------------------------------------- Helpers */

    String artistName(JSONObject item) {
        JSONObject artist = item == null ? null : item.optJSONObject("artist");
        String name = artist == null ? null : artist.optString("name", null);
        return name == null || name.length() == 0 ? str(R.string.unknown_artist) : name;
    }

    /** Artwork address for an artist or album known only from track data; misses are remembered. */
    String derivedArtwork(String kind, String id) {
        if (api.profile() == null || id == null || !id.matches("[0-9a-zA-Z-]{1,64}")) return null;
        return api.profile().origin + "/api/v1/mobile/" + kind + "/" + id + "/artwork";
    }

    Bitmap stagePhoto() {
        if (stage == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            stage = BitmapFactory.decodeResource(getResources(), R.drawable.connect_hero, options);
        }
        return stage;
    }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception error) { return ""; }
    }

    String relativeTime(String iso) {
        if (iso == null || iso.length() < 19 || "null".equals(iso)) return str(R.string.time_unknown);
        try {
            String normalised = iso.replaceAll("\\.\\d+", "").replaceAll("Z$", "+0000").replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2");
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(normalised);
            long minutes = (System.currentTimeMillis() - date.getTime()) / 60000L;
            if (minutes < 1) return str(R.string.time_now);
            if (minutes < 60) return plural(R.plurals.minutes_ago, (int) minutes);
            if (minutes < 60 * 24) return plural(R.plurals.hours_ago, (int) (minutes / 60));
            if (minutes < 60 * 24 * 30) return plural(R.plurals.days_ago, (int) (minutes / (60 * 24)));
            return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date);
        } catch (Exception error) {
            return str(R.string.time_unknown);
        }
    }

    @Override protected void onDestroy() {
        if (player != null) player.stop();
        if (artwork != null) artwork.close();
        worker.shutdownNow();
        super.onDestroy();
    }
}
