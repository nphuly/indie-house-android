# Indie House cho BlackBerry Leap

[English](README.md) · **Tiếng Việt**

App Android cho [Indie House](https://github.com/nphuly/indie-house-server), dịch vụ nghe nhạc tự host chỉ dành cho người được mời.

| Repo | Nội dung |
| --- | --- |
| [indie-house-server](https://github.com/nphuly/indie-house-server) | Backend: control plane Laravel, media worker và stream gateway viết bằng Go, tài liệu |
| [indie-house-ios](https://github.com/nphuly/indie-house-ios) | App iOS native viết bằng SwiftUI |
| **indie-house-android** (repo này) | App Android 4.3 (API 18) cho BlackBerry Leap |

Đây là app Android viết bằng Java cho runtime Android 4.3 (API 18) của BlackBerry Leap. Toàn bộ giao diện dựng bằng Android Views; app không phụ thuộc WebView hay Flutter. App tìm instance Indie House từ địa chỉ HTTPS hoặc bằng cách quét cùng mã QR manifest với app iPhone. App xác nhận tên instance và hostname trước khi kết nối, kiểm tra instance ID lúc đăng nhập, và dùng [mobile JSON API](https://github.com/nphuly/indie-house-server/blob/main/docs/vi/mobile-api.md) với access token và refresh token xoay vòng.

Các màn hình và thao tác đã có: kết nối bằng địa chỉ hoặc quét QR bằng camera, đăng nhập hoặc nhận lời mời, Trang chủ, Tìm kiếm, Khám phá (toàn bộ catalogue), Thư viện, trang album, nghệ sĩ, playlist và lịch sử nghe, và Cài đặt (thư viện, chất lượng phát, tự phát, lịch sử nghe, thiết bị, đăng xuất). Người dùng tạo, sửa và xoá playlist, thêm hoặc bỏ bài khỏi playlist, xếp một bài để phát tiếp theo, và thu hồi thiết bị. Phát nhạc dùng `MediaPlayer` của Android cho luồng MP3 và FLAC (máy không giải mã được FLAC thì chuyển xuống 320 kbps), có phát ngẫu nhiên, lặp lại (tắt/tất cả/một bài), hàng đợi, và theo cài đặt tự phát của tài khoản. Token được lưu bằng một khoá AES bọc trong Android Keystore khi máy hỗ trợ; nếu runtime BlackBerry không có keystore dùng được, phiên chỉ còn tới khi tiến trình app kết thúc. Không có token nào được lưu dạng văn bản thường.

Giao diện có tiếng Việt (mặc định) và tiếng Anh, theo web nghe nhạc và [hệ thiết kế "Biển hiệu"](https://github.com/nphuly/indie-house-server/blob/main/docs/vi/design-system.md) mô tả trong repo server. Chuỗi nằm trong `res/values/strings.xml` (tiếng Việt) và `res/values-en/strings.xml`; hai file phải có cùng khoá. Ngôn ngữ đổi bằng nút VI | EN ở màn kết nối, màn đăng nhập hoặc trong Cài đặt, được `Locales` lưu theo máy, và gửi lên server qua `Accept-Language`. Đổi ngôn ngữ dựng lại giao diện tại chỗ, nên nhạc không dừng.

App dùng cùng token "Biển hiệu", font Chakra Petch và JetBrains Mono đóng gói trong `assets/fonts` (đã cắt còn Latin và tiếng Việt, kèm file giấy phép SIL Open Font License), và bộ icon của web. Nút Play, nút pill và artwork có góc vát (`Widgets.Cut`, và `clipPath` trong `ArtView`). Trong repo [indie-house-server](https://github.com/nphuly/indie-house-server), `python3 scripts/generate-legacy-android-assets.py` dựng icon từ `src/control-plane/app/Support/Icon.php` thành PNG cho từng mật độ màn hình, vì Android 4.3 không có vector drawable, và cắt ảnh hero tiệm băng đĩa (`scripts/generate-record-shop-hero.py`) cho màn kết nối và spotlight trang chủ. Script ghi vào đây khi repo này nằm cạnh ở `../indie-house-android`, hoặc khi biến `INDIE_HOUSE_ANDROID` trỏ tới đây. `ArtView` vẽ lớp hologram của web lên artwork đã tải, còn `ArtworkLoader` giải mã đủ màu ở kích thước từ 720px trở lên.

App lấy artwork của bài, nghệ sĩ và album qua endpoint HTTPS có xác thực, giữ một cache nhỏ trong bộ nhớ, nhớ các địa chỉ không có ảnh, và hiện placeholder có màu kèm chữ cái đầu khi không có artwork. Kệ album và nghệ sĩ được suy ra từ dữ liệu bài hát. Danh sách nghe gần đây hiện mỗi bài một lần, sắp theo lần nghe mới nhất; server vẫn giữ mọi lượt nghe cho lịch sử và thống kê. Giống web, một lượt nghe được ghi sau mười giây hoặc khi hết bài.

Khi mở app từ đầu, một màn hình chờ native hiện trong lúc kiểm tra phiên đã lưu và dữ liệu Trang chủ. Phiên còn hợp lệ thì mở thẳng Trang chủ; thiếu hoặc hết hạn thì mở màn đăng nhập. Lỗi kết nối và lỗi instance giữ ở màn chờ, kèm nút Thử lại và Đổi thư viện.

Bốn mục chính dùng thanh điều hướng dưới đáy: Trang chủ, Tìm kiếm, Khám phá và Thư viện. Mỗi tab giữ back stack và vị trí cuộn riêng; chạm lại tab đang chọn để về trang đầu, rồi lên đỉnh, rồi làm mới. Cài đặt mở từ avatar trên thanh trên cùng. Menu và form là bottom sheet; hành động phá huỷ hỏi xác nhận bằng hộp thoại.

Android 4.3 không có chứng chỉ ISRG Root X1 mà chuỗi HTTPS hiện tại cần. Với API dưới 25, client kiểm tra theo các root tin cậy sẵn có của máy cùng [chứng chỉ root chính thức của Let's Encrypt](https://letsencrypt.org/certs/isrgrootx1.pem) đóng gói kèm. Việc kiểm tra chuỗi chứng chỉ và hostname vẫn được bật. Android 4.1–4.4 để TLS 1.1 và 1.2 tắt sẵn cho socket client và vẫn bật SSLv3, nên API client bật TLS 1.2, 1.1 và 1.0 và bỏ SSLv3.

`MediaPlayer` stream qua tầng media riêng của Android, không đi qua client đó. Trên emulator Android 4.3, tầng này chỉ bắt tay được TLS 1.0 với cipher ECDHE-RSA CBC và chỉ tin kho chứng chỉ hệ thống. Vì vậy nhạc chỉ phát được khi edge còn nhận TLS 1.0 (mức tối thiểu mặc định của Cloudflare) và chứng chỉ nối tới một root mà máy đã tin. Kiểm tra cả hai trên Leap trước khi nâng mức TLS tối thiểu. Chứng chỉ đóng gói kèm có fingerprint SHA-256 `96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6`.

Trình quét QR dùng camera của máy và ZXing Core 3.3.2, đóng gói trong `libs/` kèm giấy phép Apache 2.0. Không cần Google Play services hay app quét mã bên ngoài. Camera không bắt buộc: người dùng luôn nhập được địa chỉ HTTPS bằng tay.

## Làm việc trong Android Studio

Mở thư mục gốc của repo này như một project rồi đợi Gradle sync. Chọn JDK đi kèm Android Studio (JBR 21 dùng được) làm Gradle JDK. Cài Android SDK Platform 35 và Build Tools 36.0.0 trong SDK Manager; `build.sh` gốc cần thêm Platform 18. Android Studio tạo `local.properties` riêng cho từng máy, chứa đường dẫn SDK. Gradle wrapper tải Gradle 9.1.0 ở lần chạy đầu. Project dùng Android Gradle Plugin 9.0.1 và biên dịch mã Java 8 cho Android 4.3 (API 18).

Module `app` dùng trực tiếp `src`, `res`, `AndroidManifest.xml` và file JAR ZXing có sẵn. Chọn run configuration `app` rồi dùng **Run** hoặc **Build > Build APK(s)**. APK của Gradle nằm ở `gradle-build/app/outputs/apk/debug/app-debug.apk`. Cần một máy Leap thật để kiểm tra phát nhạc, TLS và camera riêng của BlackBerry.

### Cấu trúc giao diện và phần xem trước

- `MainActivity` giữ phiên, khung giao diện sau đăng nhập (thanh trên, nội dung, mini player, điều hướng), back stack của từng tab, request và thao tác. `Player` bọc `MediaPlayer` cùng hàng đợi, phát ngẫu nhiên, lặp lại và ghi lượt nghe; `PlayerScreen` là màn Đang phát toàn màn hình. `QrScanActivity` lo việc quét bằng camera.
- `Screens` dựng mọi trang từ dữ liệu `Page` đã cache. `Theme` giữ token và font, `StyledActivity` các hàm dựng dùng chung, và `Sheet` các bottom sheet cùng hộp thoại xác nhận. `ArtView`, `TrackRow`, `IconButton`, `NavigationBar` và các view nhỏ trong `Widgets` (equalizer, công tắc, vạch tải, nút pill) là View dùng lại được.
- `res/layout/screen_connect.xml` và `screen_login.xml` là layout thật của màn kết nối và đăng nhập. Mở một trong hai file và chọn **Split** hoặc **Design** trong Android Studio để xem trước bằng Layout Editor khi sửa. Màu, kiểu chữ và nút nằm trong `res/values/colors.xml`, `res/values/styles.xml` và `res/drawable/`. `android:tag` của TextView (`mono`, `semibold`, `bold`, `extrabold`) chọn font đóng gói kèm lúc chạy; bản xem trước hiện font hệ thống. Thuộc tính `tools:` chỉ ảnh hưởng bản xem trước.
- `res/layout/preview_navigation.xml` và `preview_track_row.xml` hiện hai View tự viết với nội dung mẫu. Dữ liệu, tab đang chọn và callback của chúng do Java cung cấp lúc chạy; thử các màn động trên emulator hoặc máy thật và xem bằng **Layout Inspector**.

Giữ trình dựng bản xem trước ở API 18 khi kiểm tra layout cho Leap, rồi chạy trên Leap để kiểm tra font thật, vùng chạm và phát nhạc. Các màn catalogue và trình phát dựng bằng code chưa xem trước được như màn XML hoàn chỉnh cho tới khi phần layout được chuyển sang resource.

Nếu Android Studio vẫn hiện run configuration `IndieHouseLeap` cũ và báo thiếu đường dẫn APK, sync Gradle rồi chọn hoặc tạo một run configuration **Android App** với module `app` và launch option **Default Activity**. Cấu hình cũ trỏ vào gốc project, nơi không tạo ra APK.

Nếu có `build/debug.keystore`, Gradle ký APK debug bằng cùng khoá local với `build.sh`, nên cài đè được lên các bản thử trước. Giữ khoá đó khi chuyển việc phát triển sang máy khác. Ở một bản checkout mới không có khoá này, Gradle dùng khoá debug thông thường của Android Studio; APK đó không cài đè được lên app đã ký bằng khoá cũ.

## Build bằng dòng lệnh

Build với JDK và Android SDK Platform 18 và 35/Build Tools 36.0.0:

```bash
./build.sh
```

APK đã ký để thử cục bộ là `dist/indie-house-leap-api18.apk`. Bản build dùng khoá có sẵn trong thư mục `build/` (đã bị Git bỏ qua); giữ khoá này để các bản sau cài đè được. Đây là bản build thử riêng, không phải bản phát hành lên kho ứng dụng.

APK đã được biên dịch cho Android API 18, kiểm tra chữ ký v1 và cài trên một emulator Android mới hơn với chế độ cho phép target cũ. Màn kết nối và màn xem trước camera đã mở được. **Giải mã QR, HTTPS/TLS, đăng nhập, stream và tua nhạc vẫn cần thử trên một máy BlackBerry Leap thật.** Runtime Android của BlackBerry 10 có giới hạn riêng về camera, TLS và audio. App từ chối HTTP, lỗi chứng chỉ và redirect API sang origin khác; không tắt các kiểm tra này để chữa cháy cho kho chứng chỉ cũ. App chỉ làm việc với instance Indie House mà người dùng đã xác nhận.

## Giấy phép

[PolyForm Noncommercial License 1.0.0](LICENSE): chỉ dùng cho mục đích phi thương mại. Xem thêm [DISCLAIMER.md](DISCLAIMER.md#tiếng-việt). ZXing Core dùng giấy phép Apache 2.0 (`libs/`), còn các font đóng gói kèm dùng SIL Open Font License (`assets/fonts`).
