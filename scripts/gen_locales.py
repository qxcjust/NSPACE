#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate NSPACE locale string resources for the 13 newly-requested languages.

Convention (matches existing values-es/ru/th/my):
  - Translate the ~56 user-facing UI strings.
  - Keep brand names (app_*, shortcut_*) as-is (they fall back to default).
  - Keep download_url_hint (a URL) as-is.
  - Preserve Android format placeholders exactly: %1$s %2$s
  - Preserve the literal "\n" in lock_message.

lock_message uses the CURRENT (post VIN->ANDROID_ID migration) wording:
  "This device is not authorized.\nContact your supplier to have this device authorized."
"""
import os

RES_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

# Strings that are the same in every locale (brand names / a URL) and are
# simply carried through. They are NOT translated.
KEPT = {
    "app_name": "NSpace",
    "download_url_hint": "https://example.com/file.mp4",
    "shortcut_bilibili": "Bilibili",
    "shortcut_tencent": "Tencent Video",
    "shortcut_douyin": "Douyin",
    "shortcut_xigua": "Xigua",
    "shortcut_kuaishou": "Kuaishou",
    "shortcut_haokan": "Haokan Video",
    "shortcut_sohu": "Sohu Video",
    "shortcut_xiaohongshu": "Xiaohongshu",
    "shortcut_dedao": "Dedao",
    "shortcut_toutiao": "Toutiao",
    "shortcut_apple_music": "Apple Music",
}

# Per-language translations of the user-facing strings.
TR = {
    "ja": {
        "action_back": "戻る", "action_forward": "進む", "action_refresh": "更新",
        "action_home": "ホーム", "action_settings": "設定",
        "nav_open": "ナビゲーションを開く", "nav_close": "ナビゲーションを閉じる",
        "nav_home": "ホーム", "nav_bookmarks": "ブックマーク", "nav_history": "履歴",
        "nav_downloads": "ダウンロード", "nav_privacy": "プライバシー",
        "nav_settings": "設定", "nav_account": "アカウント",
        "home_search_hint": "検索またはURLを入力", "home_search_button": "検索", "action_search": "検索",
        "tab_home": "ホーム", "tab_discover": "探索", "tab_apps": "アプリ",
        "hero_title_default": "NSpaceへようこそ",
        "hero_subtitle_default": "すべてが一か所に集まるメディアハブ",
        "section_favorite_apps": "お気に入りアプリ", "section_continue": "再生を続ける",
        "section_trending": "話題のコンテンツ", "content_placeholder": "近日公開",
        "browser_go": "移動", "browser_bookmark": "ブックマーク",
        "download_start": "ダウンロードを開始", "download_clear": "完了したものを削除",
        "privacy_clear_history": "履歴を削除", "privacy_clear_cookies": "Cookieを削除",
        "privacy_clear_storage": "Webストレージを削除", "privacy_clear_all": "すべて削除",
        "privacy_cleared_history": "履歴を削除しました", "privacy_cleared_cookies": "Cookieを削除しました",
        "privacy_cleared_storage": "Webストレージを削除しました",
        "privacy_cleared_all": "すべてのプライベートデータを削除しました",
        "settings_search_engine": "デフォルトの検索エンジン", "settings_version": "バージョン %1$s",
        "account_email": "メールアドレス", "account_password": "パスワード",
        "account_sign_in": "サインイン", "account_google": "Googleで続行",
        "account_qr": "QRでサインイン", "account_sign_out": "サインアウト",
        "account_signed_in_as": "%1$s (%2$s) でサインイン中",
        "region_picker_title": "対象地域を選択", "region_follow_system": "システムの言語に従う",
        "region_badge_format": "地域 · %1$s", "region_toast_applied": "地域を変更しました：%1$s",
        "lock_title": "未認証のデバイス",
        "lock_message": "このデバイスは認証されていません。\\nこのデバイスを認証するにはサプライヤーに連絡してください。",
        "verifying_title": "起動中",
    },
    "ko": {
        "action_back": "뒤로", "action_forward": "앞으로", "action_refresh": "새로 고침",
        "action_home": "홈", "action_settings": "설정",
        "nav_open": "탐색 열기", "nav_close": "탐색 닫기",
        "nav_home": "홈", "nav_bookmarks": "북마크", "nav_history": "기록",
        "nav_downloads": "다운로드", "nav_privacy": "개인정보",
        "nav_settings": "설정", "nav_account": "계정",
        "home_search_hint": "검색하거나 URL 입력", "home_search_button": "검색", "action_search": "검색",
        "tab_home": "홈", "tab_discover": "둘러보기", "tab_apps": "앱",
        "hero_title_default": "NSpace에 오신 것을 환영합니다",
        "hero_subtitle_default": "하나의 장소에 모인 미디어 허브",
        "section_favorite_apps": "즐겨찾는 앱", "section_continue": "이어서 재생",
        "section_trending": "지금 뜨는 콘텐츠", "content_placeholder": "곧 출시 예정",
        "browser_go": "이동", "browser_bookmark": "북마크",
        "download_start": "다운로드 시작", "download_clear": "완료된 항목 지우기",
        "privacy_clear_history": "기록 지우기", "privacy_clear_cookies": "쿠키 지우기",
        "privacy_clear_storage": "웹 저장소 지우기", "privacy_clear_all": "모두 지우기",
        "privacy_cleared_history": "기록이 지워졌습니다", "privacy_cleared_cookies": "쿠키가 지워졌습니다",
        "privacy_cleared_storage": "웹 저장소가 지워졌습니다",
        "privacy_cleared_all": "모든 개인정보 데이터가 지워졌습니다",
        "settings_search_engine": "기본 검색 엔진", "settings_version": "버전 %1$s",
        "account_email": "이메일", "account_password": "비밀번호",
        "account_sign_in": "로그인", "account_google": "Google로 계속",
        "account_qr": "QR로 로그인", "account_sign_out": "로그아웃",
        "account_signed_in_as": "%1$s(%2$s)으로 로그인됨",
        "region_picker_title": "대상 지역 선택", "region_follow_system": "시스템 언어 따르기",
        "region_badge_format": "지역 · %1$s", "region_toast_applied": "지역이 변경되었습니다: %1$s",
        "lock_title": "인증되지 않은 기기",
        "lock_message": "이 기기는 인증되지 않았습니다.\\n이 기기를 인증하려면 공급업체에 문의하세요.",
        "verifying_title": "시작 중",
    },
    "vi": {
        "action_back": "Quay lại", "action_forward": "Tiến", "action_refresh": "Làm mới",
        "action_home": "Trang chủ", "action_settings": "Cài đặt",
        "nav_open": "Mở điều hướng", "nav_close": "Đóng điều hướng",
        "nav_home": "Trang chủ", "nav_bookmarks": "Dấu trang", "nav_history": "Lịch sử",
        "nav_downloads": "Tải xuống", "nav_privacy": "Quyền riêng tư",
        "nav_settings": "Cài đặt", "nav_account": "Tài khoản",
        "home_search_hint": "Tìm kiếm hoặc nhập URL", "home_search_button": "Tìm kiếm", "action_search": "Tìm kiếm",
        "tab_home": "Trang chủ", "tab_discover": "Khám phá", "tab_apps": "Ứng dụng",
        "hero_title_default": "Chào mừng đến với NSpace",
        "hero_subtitle_default": "Trung tâm giải trí của bạn, tất cả trong một",
        "section_favorite_apps": "Ứng dụng yêu thích", "section_continue": "Tiếp tục phát",
        "section_trending": "Xu hướng hiện nay", "content_placeholder": "Sắp ra mắt",
        "browser_go": "Đi", "browser_bookmark": "Đánh dấu",
        "download_start": "Bắt đầu tải xuống", "download_clear": "Xóa mục đã xong",
        "privacy_clear_history": "Xóa lịch sử", "privacy_clear_cookies": "Xóa cookie",
        "privacy_clear_storage": "Xóa bộ nhớ web", "privacy_clear_all": "Xóa tất cả",
        "privacy_cleared_history": "Đã xóa lịch sử", "privacy_cleared_cookies": "Đã xóa cookie",
        "privacy_cleared_storage": "Đã xóa bộ nhớ web",
        "privacy_cleared_all": "Đã xóa tất cả dữ liệu riêng tư",
        "settings_search_engine": "Công cụ tìm kiếm mặc định", "settings_version": "Phiên bản %1$s",
        "account_email": "Email", "account_password": "Mật khẩu",
        "account_sign_in": "Đăng nhập", "account_google": "Tiếp tục với Google",
        "account_qr": "Đăng nhập bằng QR", "account_sign_out": "Đăng xuất",
        "account_signed_in_as": "Đã đăng nhập với %1$s (%2$s)",
        "region_picker_title": "Chọn khu vực đích", "region_follow_system": "Theo ngôn ngữ hệ thống",
        "region_badge_format": "Khu vực · %1$s", "region_toast_applied": "Đã chuyển khu vực: %1$s",
        "lock_title": "Thiết bị chưa được ủy quyền",
        "lock_message": "Thiết bị này chưa được ủy quyền.\\nHãy liên hệ nhà cung cấp để được cấp quyền cho thiết bị này.",
        "verifying_title": "Đang khởi động",
    },
    "lo": {
        "action_back": "ກັບຄືນ", "action_forward": "ໄປຂ້າງຫນ້າ", "action_refresh": "ໂຫຼດໃໝ່",
        "action_home": "ໜ້າຫຼັກ", "action_settings": "ຕັ້ງຄ່າ",
        "nav_open": "ເປີດການນຳທາງ", "nav_close": "ປິດການນຳທາງ",
        "nav_home": "ໜ້າຫຼັກ", "nav_bookmarks": "ບຸກມາກ", "nav_history": "ປະຫວັດ",
        "nav_downloads": "ດາວໂຫຼດ", "nav_privacy": "ຄວາມເປັນສ່ວນຕົວ",
        "nav_settings": "ຕັ້ງຄ່າ", "nav_account": "ບັນຊີ",
        "home_search_hint": "ຄົ້ນຫາຫຼືພິມ URL", "home_search_button": "ຄົ້ນຫາ", "action_search": "ຄົ້ນຫາ",
        "tab_home": "ໜ້າຫຼັກ", "tab_discover": "ສຳລວດ", "tab_apps": "ແອັບ",
        "hero_title_default": "ຍິນດີຕ້ອນຮັບສູ່ NSpace",
        "hero_subtitle_default": "ສູນສື່ຂອງທ່ານ, ທັງໝົດໃນບ່ອນດຽວ",
        "section_favorite_apps": "ແອັບທີ່ມັກ", "section_continue": "ສືບຕໍ່ການລົງສະແດງ",
        "section_trending": "ກຳລັງນິຍົມ", "content_placeholder": "ໄວ້ລໍຖ້າ",
        "browser_go": "ໄປ", "browser_bookmark": "ບຸກມາກ",
        "download_start": "ເລີ່ມດາວໂຫຼດ", "download_clear": "ລົບລາຍການທີ່ສຳເລັດ",
        "privacy_clear_history": "ລົບປະຫວັດ", "privacy_clear_cookies": "ລົບຄຸກກີ້",
        "privacy_clear_storage": "ລົບພື້ນທີ່ເກັບຂໍ້ມູນເວັບ", "privacy_clear_all": "ລົບທັງໝົດ",
        "privacy_cleared_history": "ລົບປະຫວັດແລ້ວ", "privacy_cleared_cookies": "ລົບຄຸກກີ້ແລ້ວ",
        "privacy_cleared_storage": "ລົບພື້ນທີ່ເກັບຂໍ້ມູນເວັບແລ້ວ",
        "privacy_cleared_all": "ລົບຂໍ້ມູນສ່ວນຕົວທັງໝົດແລ້ວ",
        "settings_search_engine": "ເຄື່ອງມືຄົ້ນຫາເລີ່ມຕົ້ນ", "settings_version": "ລຸ້ນ %1$s",
        "account_email": "ອີເມລ", "account_password": "ລະຫັດຜ່ານ",
        "account_sign_in": "ເຂົ້າສູ່ລະບົບ", "account_google": "ສືບຕໍ່ດ້ວຍ Google",
        "account_qr": "ເຂົ້າສູ່ລະບົບດ້ວຍ QR", "account_sign_out": "ອອກຈາກລະບົບ",
        "account_signed_in_as": "ເຂົ້າສູ່ລະບົບເປັນ %1$s (%2$s)",
        "region_picker_title": "ເລືອກພາກພື້ນເປົ້າຫມາຍ", "region_follow_system": "ປະຕິບັດຕາມພາສາລະບົບ",
        "region_badge_format": "ພາກພື້ນ · %1$s", "region_toast_applied": "ປ່ຽນພາກພື້ນແລ້ວ: %1$s",
        "lock_title": "ອຸປະກອນບໍ່ຮັບອະນຸຍາດ",
        "lock_message": "ອຸປະກອນນີ້ບໍ່ໄດ້ຮັບອະນຸຍາດ.\\nຕິດຕໍ່ຜູ້ສະໜອງເພື່ອໃຫ້ອະນຸຍາດອຸປະກອນນີ້.",
        "verifying_title": "ກຳລັງເລີ່ມຕົ້ນ",
    },
    "km": {
        "action_back": "ត្រឡប់ក្រោយ", "action_forward": "បន្តទៅមុខ", "action_refresh": "ផ្ទុកឡើងវិញ",
        "action_home": "ទំព័រដើម", "action_settings": "ការកំណត់",
        "nav_open": "បើកការរុករក", "nav_close": "បិទការរុករក",
        "nav_home": "ទំព័រដើម", "nav_bookmarks": "ចំណាំ", "nav_history": "ប្រវត្តិ",
        "nav_downloads": "ការទាញយក", "nav_privacy": "ឯកជនភាព",
        "nav_settings": "ការកំណត់", "nav_account": "គណនី",
        "home_search_hint": "ស្វែងរក ឬវាយ URL", "home_search_button": "ស្វែងរក", "action_search": "ស្វែងរក",
        "tab_home": "ទំព័រដើម", "tab_discover": "រុករក", "tab_apps": "កម្មវិធី",
        "hero_title_default": "ស្វាគមន៍មកកាន់ NSpace",
        "hero_subtitle_default": "មជ្ឈមណ្ឌលប្រព័ន្ធផ្សព្វផ្សាយរបស់អ្នក ទាំងអស់នៅកន្លែងតែមួយ",
        "section_favorite_apps": "កម្មវិធីដែលចូលចិត្ត", "section_continue": "បន្តការចាក់",
        "section_trending": "កំពុងពេញនិយម", "content_placeholder": "ផ្តល់ជូនក្នុងពេលឆាប់ៗ",
        "browser_go": "ទៅ", "browser_bookmark": "ចំណាំ",
        "download_start": "ចាប់ផ្តើមទាញយក", "download_clear": "សម្អាតអ្វីដែលបានបញ្ចប់",
        "privacy_clear_history": "សម្អាតប្រវត្តិ", "privacy_clear_cookies": "សម្អាតខូគី",
        "privacy_clear_storage": "សម្អាតការផ្ទុកបណ្តាញ", "privacy_clear_all": "សម្អាតទាំងអស់",
        "privacy_cleared_history": "បានសម្អាតប្រវត្តិ", "privacy_cleared_cookies": "បានសម្អាតខូគី",
        "privacy_cleared_storage": "បានសម្អាតការផ្ទុកបណ្តាញ",
        "privacy_cleared_all": "បានសម្អាតទិន្នន័យឯកជនទាំងអស់",
        "settings_search_engine": "ម៉ាស៊ីនស្វែងរកលំនាំដើម", "settings_version": "កំណែ %1$s",
        "account_email": "អ៊ីមែល", "account_password": "ពាក្យសម្ងាត់",
        "account_sign_in": "ចូល", "account_google": "បន្តជាមួយ Google",
        "account_qr": "ចូលជាមួយ QR", "account_sign_out": "ចាកចេញ",
        "account_signed_in_as": "បានចូលជា %1$s (%2$s)",
        "region_picker_title": "ជ្រើសរើសតំបន់គោលដៅ", "region_follow_system": "តាមភាសាប្រព័ន្ធ",
        "region_badge_format": "តំបន់ · %1$s", "region_toast_applied": "បានប្តូរតំបន់៖ %1$s",
        "lock_title": "ឧបករណ៍មិនបានអនុញ្ញាត",
        "lock_message": "ឧបករណ៍នេះមិនត្រូវបានអនុញ្ញាតទេ。\\nសូមទាក់ទងអ្នកផ្គត់ផ្គង់ដើម្បីអនុញ្ញាតឧបករណ៍នេះ។",
        "verifying_title": "កំពុងចាប់ផ្តើម",
    },
    "in": {
        "action_back": "Kembali", "action_forward": "Maju", "action_refresh": "Segarkan",
        "action_home": "Beranda", "action_settings": "Pengaturan",
        "nav_open": "Buka navigasi", "nav_close": "Tutup navigasi",
        "nav_home": "Beranda", "nav_bookmarks": "Penanda", "nav_history": "Riwayat",
        "nav_downloads": "Unduhan", "nav_privacy": "Privasi",
        "nav_settings": "Pengaturan", "nav_account": "Akun",
        "home_search_hint": "Cari atau ketik URL", "home_search_button": "Cari", "action_search": "Cari",
        "tab_home": "Beranda", "tab_discover": "Jelajahi", "tab_apps": "Aplikasi",
        "hero_title_default": "Selamat datang di NSpace",
        "hero_subtitle_default": "Pusat media Anda, semua dalam satu tempat",
        "section_favorite_apps": "Aplikasi favorit", "section_continue": "Lanjutkan pemutaran",
        "section_trending": "Sedang tren", "content_placeholder": "Segera hadir",
        "browser_go": "Buka", "browser_bookmark": "Tandai",
        "download_start": "Mulai unduh", "download_clear": "Hapus yang selesai",
        "privacy_clear_history": "Hapus riwayat", "privacy_clear_cookies": "Hapus cookie",
        "privacy_clear_storage": "Hapus penyimpanan web", "privacy_clear_all": "Hapus semua",
        "privacy_cleared_history": "Riwayat dihapus", "privacy_cleared_cookies": "Cookie dihapus",
        "privacy_cleared_storage": "Penyimpanan web dihapus",
        "privacy_cleared_all": "Semua data pribadi dihapus",
        "settings_search_engine": "Mesin pencari default", "settings_version": "Versi %1$s",
        "account_email": "Email", "account_password": "Kata sandi",
        "account_sign_in": "Masuk", "account_google": "Lanjut dengan Google",
        "account_qr": "Masuk dengan QR", "account_sign_out": "Keluar",
        "account_signed_in_as": "Masuk sebagai %1$s (%2$s)",
        "region_picker_title": "Pilih wilayah tujuan", "region_follow_system": "Ikuti bahasa sistem",
        "region_badge_format": "Wilayah · %1$s", "region_toast_applied": "Wilayah diubah: %1$s",
        "lock_title": "Perangkat tidak diizinkan",
        "lock_message": "Perangkat ini tidak diizinkan.\\nHubungi pemasok Anda untuk mengizinkan perangkat ini.",
        "verifying_title": "Memulai",
    },
    "hi": {
        "action_back": "वापस", "action_forward": "आगे", "action_refresh": "रीफ्रेश",
        "action_home": "होम", "action_settings": "सेटिंग",
        "nav_open": "नेविगेशन खोलें", "nav_close": "नेविगेशन बंद करें",
        "nav_home": "होम", "nav_bookmarks": "बुकमार्क", "nav_history": "इतिहास",
        "nav_downloads": "डाउनलोड", "nav_privacy": "गोपनीयता",
        "nav_settings": "सेटिंग", "nav_account": "खाता",
        "home_search_hint": "खोजें या URL टाइप करें", "home_search_button": "खोज", "action_search": "खोज",
        "tab_home": "होम", "tab_discover": "खोजें", "tab_apps": "ऐप्स",
        "hero_title_default": "NSpace में आपका स्वागत है",
        "hero_subtitle_default": "आपका मीडिया हब, सब कुछ एक जगह",
        "section_favorite_apps": "पसंदीदा ऐप्स", "section_continue": "प्ले जारी रखें",
        "section_trending": "ट्रेंडिंग", "content_placeholder": "जल्द आ रहा है",
        "browser_go": "जाएं", "browser_bookmark": "बुकमार्क",
        "download_start": "डाउनलोड शुरू करें", "download_clear": "पूर्ण हुए हटाएं",
        "privacy_clear_history": "इतिहास साफ करें", "privacy_clear_cookies": "कुकीज़ साफ करें",
        "privacy_clear_storage": "वेब स्टोरेज साफ करें", "privacy_clear_all": "सब कुछ साफ करें",
        "privacy_cleared_history": "इतिहास साफ़ हो गया", "privacy_cleared_cookies": "कुकीज़ साफ़ हो गईं",
        "privacy_cleared_storage": "वेब स्टोरेज साफ़ हो गया",
        "privacy_cleared_all": "सारा निजी डेटा साफ़ हो गया",
        "settings_search_engine": "डिफ़ॉल्ट सर्च इंजन", "settings_version": "संस्करण %1$s",
        "account_email": "ईमेल", "account_password": "पासवर्ड",
        "account_sign_in": "साइन इन", "account_google": "Google के साथ जारी रखें",
        "account_qr": "QR से साइन इन करें", "account_sign_out": "साइन आउट",
        "account_signed_in_as": "%1$s (%2$s) के रूप में साइन इन",
        "region_picker_title": "लक्ष्य क्षेत्र चुनें", "region_follow_system": "सिस्टम भाषा का पालन करें",
        "region_badge_format": "क्षेत्र · %1$s", "region_toast_applied": "क्षेत्र बदला गया: %1$s",
        "lock_title": "अधिकृत नहीं किया गया डिवाइस",
        "lock_message": "यह डिवाइस अधिकृत नहीं है।\\nइस डिवाइस को अधिकृत करने के लिए अपने आपूर्तिकर्ता से संपर्क करें।",
        "verifying_title": "शुरू हो रहा है",
    },
    "ne": {
        "action_back": "फिर्ता", "action_forward": "अघि", "action_refresh": "रिफ्रेस",
        "action_home": "गृह", "action_settings": "सेटिङ",
        "nav_open": "नेभिगेसन खोल्नुहोस्", "nav_close": "नेभिगेसन बन्द गर्नुहोस्",
        "nav_home": "गृह", "nav_bookmarks": "बुकमार्क", "nav_history": "इतिहास",
        "nav_downloads": "डाउनलोड", "nav_privacy": "गोपनीयता",
        "nav_settings": "सेटिङ", "nav_account": "खाता",
        "home_search_hint": "खोज्नुहोस् वा URL टाइप गर्नुहोस्", "home_search_button": "खोज", "action_search": "खोज",
        "tab_home": "गृह", "tab_discover": "अन्वेषण", "tab_apps": "एपहरू",
        "hero_title_default": "NSpace मा स्वागत छ",
        "hero_subtitle_default": "तपाईंको मिडिया हब, सबै एकै ठाउँमा",
        "section_favorite_apps": "मनपर्ने एपहरू", "section_continue": "प्ले जारी राख्नुहोस्",
        "section_trending": "ट्रेन्डिङ", "content_placeholder": "चाँडै आउँदै",
        "browser_go": "जानुहोस्", "browser_bookmark": "बुकमार्क",
        "download_start": "डाउनलोड सुरु गर्नुहोस्", "download_clear": "समाप्त भएको हटाउनुहोस्",
        "privacy_clear_history": "इतिहास हटाउनुहोस्", "privacy_clear_cookies": "कुकीहरू हटाउनुहोस्",
        "privacy_clear_storage": "वेब स्टोरेज हटाउनुहोस्", "privacy_clear_all": "सबै हटाउनुहोस्",
        "privacy_cleared_history": "इतिहास हटाइयो", "privacy_cleared_cookies": "कुकीहरू हटाइए",
        "privacy_cleared_storage": "वेब स्टोरेज हटाइयो",
        "privacy_cleared_all": "सबै निजी डेटा हटाइयो",
        "settings_search_engine": "डिफल्ट खोज इन्जिन", "settings_version": "संस्करण %1$s",
        "account_email": "इमेल", "account_password": "पासवर्ड",
        "account_sign_in": "साइन इन", "account_google": "Google बाट जारी राख्नुहोस्",
        "account_qr": "QR बाट साइन इन", "account_sign_out": "साइन आउट",
        "account_signed_in_as": "%1$s (%2$s) को रूपमा साइन इन",
        "region_picker_title": "लक्षित क्षेत्र छान्नुहोस्", "region_follow_system": "प्रणाली भाषा पछ्याउनुहोस्",
        "region_badge_format": "क्षेत्र · %1$s", "region_toast_applied": "क्षेत्र परिवर्तन गरियो: %1$s",
        "lock_title": "अनाधिकृत यन्त्र",
        "lock_message": "यो यन्त्र अनाधिकृत छ।\\nयस यन्त्रलाई अधिकृत गराउन आपूर्तिकर्तालाई सम्पर्क गर्नुहोस्।",
        "verifying_title": "सुरु हुँदै",
    },
    "kk": {
        "action_back": "Артқа", "action_forward": "Алға", "action_refresh": "Жаңарту",
        "action_home": "Басты бет", "action_settings": "Параметрлер",
        "nav_open": "Навигацияны ашу", "nav_close": "Навигацияны жабу",
        "nav_home": "Басты бет", "nav_bookmarks": "Бетбелгілер", "nav_history": "Тарих",
        "nav_downloads": "Жүктемелер", "nav_privacy": "Жекелік",
        "nav_settings": "Параметрлер", "nav_account": "Аккаунт",
        "home_search_hint": "Іздеу немесе URL енгізу", "home_search_button": "Іздеу", "action_search": "Іздеу",
        "tab_home": "Басты бет", "tab_discover": "Зерттеу", "tab_apps": "Қолданбалар",
        "hero_title_default": "NSpace қош келдіңіз",
        "hero_subtitle_default": "Сіздің медиа орталығыңыз, бәрі бір жерде",
        "section_favorite_apps": "Таңдаулы қолданбалар", "section_continue": "Ойнатуды жалғастыру",
        "section_trending": "Қазір трендте", "content_placeholder": "Жақында",
        "browser_go": "Өту", "browser_bookmark": "Бетбелгі",
        "download_start": "Жүктеуді бастау", "download_clear": "Аяқталғандарды өшіру",
        "privacy_clear_history": "Тарихты өшіру", "privacy_clear_cookies": "Cookie өшіру",
        "privacy_clear_storage": "Веб-сақтауды өшіру", "privacy_clear_all": "Барлығын өшіру",
        "privacy_cleared_history": "Тарих өшірілді", "privacy_cleared_cookies": "Cookie өшірілді",
        "privacy_cleared_storage": "Веб-сақтау өшірілді",
        "privacy_cleared_all": "Барлық жеке деректер өшірілді",
        "settings_search_engine": "Әдепкі іздеу жүйесі", "settings_version": "Нұсқа %1$s",
        "account_email": "Электрондық пошта", "account_password": "Құпия сөз",
        "account_sign_in": "Кіру", "account_google": "Google арқылы жалғастыру",
        "account_qr": "QR арқылы кіру", "account_sign_out": "Шығу",
        "account_signed_in_as": "%1$s (%2$s) ретінде кірді",
        "region_picker_title": "Мақсатты өңірді таңдау", "region_follow_system": "Жүйе тілін қолдану",
        "region_badge_format": "Өңір · %1$s", "region_toast_applied": "Өңір өзгертілді: %1$s",
        "lock_title": "Рұқсатсыз құрылғы",
        "lock_message": "Бұл құрылғыға рұқсат берілмеген.\\nОсы құрылғыға рұқсат беру үшін жеткізушіңізге хабарласыңыз.",
        "verifying_title": "Іске қосылуда",
    },
    "uz": {
        "action_back": "Orqaga", "action_forward": "Oldinga", "action_refresh": "Yangilash",
        "action_home": "Bosh sahifa", "action_settings": "Sozlamalar",
        "nav_open": "Navigatsiyani oching", "nav_close": "Navigatsiyani yoping",
        "nav_home": "Bosh sahifa", "nav_bookmarks": "Xatcho'plar", "nav_history": "Tarix",
        "nav_downloads": "Yuklamalar", "nav_privacy": "Maxfiylik",
        "nav_settings": "Sozlamalar", "nav_account": "Hisob",
        "home_search_hint": "Qidirish yoki URL kiriting", "home_search_button": "Qidirish", "action_search": "Qidirish",
        "tab_home": "Bosh sahifa", "tab_discover": "Kashf etish", "tab_apps": "Ilovalar",
        "hero_title_default": "NSpace ga xush kelibsiz",
        "hero_subtitle_default": "Sizning media markazingiz, hammasi bitta joyda",
        "section_favorite_apps": "Sevimli ilovalar", "section_continue": "Davom ettirish",
        "section_trending": "Trenddagi", "content_placeholder": "Tez orada",
        "browser_go": "O'tish", "browser_bookmark": "Xatcho'p",
        "download_start": "Yuklashni boshlash", "download_clear": "Tugallanganlarni tozalash",
        "privacy_clear_history": "Tarixni tozalash", "privacy_clear_cookies": "Cookie-larini tozalash",
        "privacy_clear_storage": "Veb-xotirani tozalash", "privacy_clear_all": "Hammasini tozalash",
        "privacy_cleared_history": "Tarix tozalandi", "privacy_cleared_cookies": "Cookie-lar tozalandi",
        "privacy_cleared_storage": "Veb-xotira tozalandi",
        "privacy_cleared_all": "Barcha shaxsiy ma'lumotlar tozalandi",
        "settings_search_engine": "Standart qidiruv tizimi", "settings_version": "Versiya %1$s",
        "account_email": "Email", "account_password": "Parol",
        "account_sign_in": "Kirish", "account_google": "Google orqali davom etish",
        "account_qr": "QR orqali kirish", "account_sign_out": "Chiqish",
        "account_signed_in_as": "%1$s (%2$s) sifatida kirildi",
        "region_picker_title": "Maqsadli hududni tanlang", "region_follow_system": "Tizim tiliga amal qilish",
        "region_badge_format": "Hudud · %1$s", "region_toast_applied": "Hudud o'zgartirildi: %1$s",
        "lock_title": "Ruxsatsiz qurilma",
        "lock_message": "Bu qurilma ruxsat etilmagan.\\nBu qurilmani ruxsatlash uchun yetkazib beruvchingizga murojaat qiling.",
        "verifying_title": "Ishga tushirilmoqda",
    },
    "mn": {
        "action_back": "Буцах", "action_forward": "Урагш", "action_refresh": "Шинэчлэх",
        "action_home": "Нүүр", "action_settings": "Тохиргоо",
        "nav_open": "Навигацыг нээх", "nav_close": "Навигацыг хаах",
        "nav_home": "Нүүр", "nav_bookmarks": "Тэмдэглэл", "nav_history": "Түүх",
        "nav_downloads": "Татан авалт", "nav_privacy": "Нууцлал",
        "nav_settings": "Тохиргоо", "nav_account": "Бүртгэл",
        "home_search_hint": "Хайх эсвэл URL бичих", "home_search_button": "Хайх", "action_search": "Хайх",
        "tab_home": "Нүүр", "tab_discover": "Судлах", "tab_apps": "Апп",
        "hero_title_default": "NSpace-д тавтай морилно уу",
        "hero_subtitle_default": "Таны медиа төв, бүгд газрын зугаар",
        "section_favorite_apps": "Дуртай апп", "section_continue": "Дахин тоглуулах",
        "section_trending": "Одоо тренд", "content_placeholder": "Удахгүй",
        "browser_go": "Орох", "browser_bookmark": "Тэмдэглэл",
        "download_start": "Татан авалтыг эхлүүлэх", "download_clear": "Дууссаныг цэвэрлэх",
        "privacy_clear_history": "Түүхийг цэвэрлэх", "privacy_clear_cookies": "Cookie цэвэрлэх",
        "privacy_clear_storage": "Вэб санах ойг цэвэрлэх", "privacy_clear_all": "Бүгдийг цэвэрлэх",
        "privacy_cleared_history": "Түүх цэвэрлэгдсэн", "privacy_cleared_cookies": "Cookie цэвэрлэгдсэн",
        "privacy_cleared_storage": "Вэб санах ой цэвэрлэгдсэн",
        "privacy_cleared_all": "Бүх хувийн өгөгдөл цэвэрлэгдсэн",
        "settings_search_engine": "Анхдагч хайлтын систем", "settings_version": "Хувилбар %1$s",
        "account_email": "И-мэйл", "account_password": "Нууц үг",
        "account_sign_in": "Нэвтрэх", "account_google": "Google-ээр үргэлжлүүлэх",
        "account_qr": "QR-ээр нэвтрэх", "account_sign_out": "Гарах",
        "account_signed_in_as": "%1$s (%2$s) ээр нэвтэрсэн",
        "region_picker_title": "Оноосон бүсийг сонгох", "region_follow_system": "Системийн хэлийг дагах",
        "region_badge_format": "Бүс · %1$s", "region_toast_applied": "Бүс солигдлоо: %1$s",
        "lock_title": "Зөвшөөрөлгүй төхөөрөмж",
        "lock_message": "Энэ төхөөрөмж зөвшөөрөгдөөгүй.\\nЭнэ төхөөрөмжийг зөвшөөрүүлэхийн тулд нийлүүлэгчтэйгээ холбогдоно уу.",
        "verifying_title": "Эхэлж байна",
    },
    "ar": {
        "action_back": "رجوع", "action_forward": "للأمام", "action_refresh": "تحديث",
        "action_home": "الرئيسية", "action_settings": "الإعدادات",
        "nav_open": "فتح التنقل", "nav_close": "إغلاق التنقل",
        "nav_home": "الرئيسية", "nav_bookmarks": "الإشارات المرجعية", "nav_history": "السجل",
        "nav_downloads": "التنزيلات", "nav_privacy": "الخصوصية",
        "nav_settings": "الإعدادات", "nav_account": "الحساب",
        "home_search_hint": "ابحث أو اكتب رابط URL", "home_search_button": "بحث", "action_search": "بحث",
        "tab_home": "الرئيسية", "tab_discover": "اكتشف", "tab_apps": "التطبيقات",
        "hero_title_default": "مرحبًا بك في NSpace",
        "hero_subtitle_default": "مركز الوسائط الخاص بك، كل شيء في مكان واحد",
        "section_favorite_apps": "التطبيقات المفضلة", "section_continue": "متابعة التشغيل",
        "section_trending": "الأكثر رواجًا الآن", "content_placeholder": "قريبًا",
        "browser_go": "انتقال", "browser_bookmark": "إشارة مرجعية",
        "download_start": "بدء التنزيل", "download_clear": "مسح المكتمل",
        "privacy_clear_history": "مسح السجل", "privacy_clear_cookies": "مسح ملفات الارتباط",
        "privacy_clear_storage": "مسح التخزين على الويب", "privacy_clear_all": "مسح الكل",
        "privacy_cleared_history": "تم مسح السجل", "privacy_cleared_cookies": "تم مسح ملفات الارتباط",
        "privacy_cleared_storage": "تم مسح التخزين على الويب",
        "privacy_cleared_all": "تم مسح جميع البيانات الخاصة",
        "settings_search_engine": "محرك البحث الافتراضي", "settings_version": "الإصدار %1$s",
        "account_email": "البريد الإلكتروني", "account_password": "كلمة المرور",
        "account_sign_in": "تسجيل الدخول", "account_google": "المتابعة مع Google",
        "account_qr": "تسجيل الدخول عبر رمز QR", "account_sign_out": "تسجيل الخروج",
        "account_signed_in_as": "تم تسجيل الدخول باسم %1$s (%2$s)",
        "region_picker_title": "اختر المنطقة المستهدفة", "region_follow_system": "اتبع لغة النظام",
        "region_badge_format": "المنطقة · %1$s", "region_toast_applied": "تم تغيير المنطقة: %1$s",
        "lock_title": "جهاز غير مصرح به",
        "lock_message": "هذا الجهاز غير مصرح به.\\nاتصل بموردك للسماح بهذا الجهاز.",
        "verifying_title": "جارٍ البدء",
    },
    "fa": {
        "action_back": "بازگشت", "action_forward": "جلو", "action_refresh": "تازه‌سازی",
        "action_home": "خانه", "action_settings": "تنظیمات",
        "nav_open": "باز کردن پیمایش", "nav_close": "بستن پیمایش",
        "nav_home": "خانه", "nav_bookmarks": "نشانک‌ها", "nav_history": "تاریخچه",
        "nav_downloads": "دانلودها", "nav_privacy": "حریم خصوصی",
        "nav_settings": "تنظیمات", "nav_account": "حساب کاربری",
        "home_search_hint": "جستجو یا وارد کردن نشانی URL", "home_search_button": "جستجو", "action_search": "جستجو",
        "tab_home": "خانه", "tab_discover": "کشف", "tab_apps": "برنامه‌ها",
        "hero_title_default": "به NSpace خوش آمدید",
        "hero_subtitle_default": "مرکز رسانه‌ای شما، همه در یک جا",
        "section_favorite_apps": "برنامه‌های مورد علاقه", "section_continue": "ادامه پخش",
        "section_trending": "روندهای فعلی", "content_placeholder": "به زودی",
        "browser_go": "برو", "browser_bookmark": "نشانک",
        "download_start": "شروع دانلود", "download_clear": "پاک کردن موارد کامل‌شده",
        "privacy_clear_history": "پاک کردن تاریخچه", "privacy_clear_cookies": "پاک کردن کوکی‌ها",
        "privacy_clear_storage": "پاک کردن حافظه وب", "privacy_clear_all": "پاک کردن همه",
        "privacy_cleared_history": "تاریخچه پاک شد", "privacy_cleared_cookies": "کوکی‌ها پاک شدند",
        "privacy_cleared_storage": "حافظه وب پاک شد",
        "privacy_cleared_all": "تمام داده‌های خصوصی پاک شد",
        "settings_search_engine": "موتور جستجوی پیش‌فرض", "settings_version": "نسخه %1$s",
        "account_email": "ایمیل", "account_password": "گذرواژه",
        "account_sign_in": "ورود", "account_google": "ادامه با Google",
        "account_qr": "ورود با QR", "account_sign_out": "خروج",
        "account_signed_in_as": "وارد شده به نام %1$s (%2$s)",
        "region_picker_title": "انتخاب منطقه هدف", "region_follow_system": "پیروی از زبان سیستم",
        "region_badge_format": "منطقه · %1$s", "region_toast_applied": "منطقه تغییر کرد: %1$s",
        "lock_title": "دستگاه غیرمجاز",
        "lock_message": "این دستگاه مجاز نیست.\\nبرای مجاز کردن این دستگاه با تامین‌کننده خود تماس بگیرید.",
        "verifying_title": "در حال شروع",
    },
}

# Canonical order of user-facing keys (for stable output).
ORDER = [
    "action_back", "action_forward", "action_refresh", "action_home", "action_settings",
    "nav_open", "nav_close", "nav_home", "nav_bookmarks", "nav_history", "nav_downloads",
    "nav_privacy", "nav_settings", "nav_account",
    "home_search_hint", "home_search_button", "action_search",
    "tab_home", "tab_discover", "tab_apps",
    "hero_title_default", "hero_subtitle_default",
    "section_favorite_apps", "section_continue", "section_trending", "content_placeholder",
    "browser_go", "browser_bookmark",
    "download_start", "download_clear",
    "privacy_clear_history", "privacy_clear_cookies", "privacy_clear_storage",
    "privacy_clear_all", "privacy_cleared_history", "privacy_cleared_cookies",
    "privacy_cleared_storage", "privacy_cleared_all",
    "settings_search_engine", "settings_version",
    "account_email", "account_password", "account_sign_in", "account_google",
    "account_qr", "account_sign_out", "account_signed_in_as",
    "region_picker_title", "region_follow_system", "region_badge_format", "region_toast_applied",
    "lock_title", "lock_message",
    "verifying_title",
]

# Kept keys appended after the translated block (same as default / es).
KEPT_ORDER = [
    "app_name", "download_url_hint",
    "shortcut_bilibili", "shortcut_tencent", "shortcut_douyin", "shortcut_xigua",
    "shortcut_kuaishou", "shortcut_haokan", "shortcut_sohu", "shortcut_xiaohongshu",
    "shortcut_dedao", "shortcut_toutiao", "shortcut_apple_music",
]


def xml_escape(s: str) -> str:
    # Android string resources require a literal apostrophe to be escaped as
    # \' (otherwise aapt2 fails with "Invalid unicode escape sequence").
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace("'", "\\'"))


def build(lang: str) -> str:
    tr = TR[lang]
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- NSpace — %s (%s) localization -->' % (lang, LANG_NAMES[lang]),
             '<resources>']
    lines.append('  <!-- Sidebar navigation actions -->')
    for k in ["action_back", "action_forward", "action_refresh", "action_home", "action_settings"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Navigation drawer -->')
    for k in ["nav_open", "nav_close", "nav_home", "nav_bookmarks", "nav_history",
              "nav_downloads", "nav_privacy", "nav_settings", "nav_account"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Home -->')
    for k in ["home_search_hint", "home_search_button", "action_search"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Top navigation tabs -->')
    for k in ["tab_home", "tab_discover", "tab_apps"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Hero banner -->')
    for k in ["hero_title_default", "hero_subtitle_default"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Content sections -->')
    for k in ["section_favorite_apps", "section_continue", "section_trending", "content_placeholder"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Browser -->')
    for k in ["browser_go", "browser_bookmark"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Downloads -->')
    for k in ["download_start", "download_clear"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Privacy -->')
    for k in ["privacy_clear_history", "privacy_clear_cookies", "privacy_clear_storage",
              "privacy_clear_all", "privacy_cleared_history", "privacy_cleared_cookies",
              "privacy_cleared_storage", "privacy_cleared_all"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Settings -->')
    for k in ["settings_search_engine", "settings_version"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Account -->')
    for k in ["account_email", "account_password", "account_sign_in", "account_google",
              "account_qr", "account_sign_out", "account_signed_in_as"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Home app-shortcut labels (brand names kept as-is across locales) -->')
    lines.append('  <string name="app_name">%s</string>' % xml_escape(KEPT["app_name"]))
    lines.append('  <string name="download_url_hint">%s</string>' % xml_escape(KEPT["download_url_hint"]))
    for k in ["shortcut_bilibili", "shortcut_tencent", "shortcut_douyin", "shortcut_xigua",
              "shortcut_kuaishou", "shortcut_haokan", "shortcut_sohu", "shortcut_xiaohongshu",
              "shortcut_dedao", "shortcut_toutiao", "shortcut_apple_music"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(KEPT[k])))
    lines.append('  <!-- Region switcher -->')
    for k in ["region_picker_title", "region_follow_system", "region_badge_format", "region_toast_applied"]:
        lines.append('  <string name="%s">%s</string>' % (k, xml_escape(tr[k])))
    lines.append('  <!-- Device lock screen (not authorized via remote allowlist) -->')
    lines.append('  <string name="lock_title">%s</string>' % xml_escape(tr["lock_title"]))
    lines.append('  <string name="lock_message">%s</string>' % xml_escape(tr["lock_message"]))
    lines.append('  <!-- Authorization verification screen (ANDROID_ID remote allowlist) -->')
    lines.append('  <string name="verifying_title">%s</string>' % xml_escape(tr["verifying_title"]))
    lines.append('</resources>')
    return "\n".join(lines) + "\n"


LANG_NAMES = {
    "ja": "Japanese", "ko": "Korean", "vi": "Vietnamese", "lo": "Lao", "km": "Khmer",
    "in": "Indonesian", "hi": "Hindi", "ne": "Nepali", "kk": "Kazakh", "uz": "Uzbek",
    "mn": "Mongolian", "ar": "Arabic", "fa": "Persian",
}


def main():
    for lang in TR:
        d = os.path.join(RES_DIR, "values-%s" % lang)
        os.makedirs(d, exist_ok=True)
        p = os.path.join(d, "strings.xml")
        with open(p, "w", encoding="utf-8") as f:
            f.write(build(lang))
        n = len(TR[lang]) + len(KEPT)
        print("wrote %s (%d strings)" % (p, n))


if __name__ == "__main__":
    main()
