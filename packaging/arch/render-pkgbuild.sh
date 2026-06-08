#!/usr/bin/env bash
set -euo pipefail

version="${1:?usage: render-pkgbuild.sh <version> <sha256> [asset-name] [release-tag]}"
sha256="${2:?usage: render-pkgbuild.sh <version> <sha256> [asset-name] [release-tag]}"
asset_name="${3:-SimpMusic-linux-x86_64.tar.gz}"

version="${version#v}"
release_tag="${4:-v${version}}"

cat <<EOF
# Maintainer: CamelliaV

pkgname=simpmusic-camelliav-bin
pkgver=${version}
pkgrel=1
pkgdesc="SimpMusic CamelliaV fork with YouTube video-as-music fallback"
arch=('x86_64')
url="https://github.com/CamelliaV/SimpMusic"
license=('GPL-3.0-or-later')
depends=(
  'bash'
  'fontconfig'
  'freetype2'
  'glibc'
  'java-runtime>=21'
  'libglvnd'
  'libpulse'
  'libx11'
  'libxext'
  'libxi'
  'libxrender'
  'libxtst'
  'nss'
  'zlib'
)
optdepends=(
  'kwallet: import Chromium YouTube cookies from KDE KWallet-backed profiles'
  'kwalletmanager: inspect KWallet state while debugging browser-cookie login'
  'vlc: fallback system VLC runtime if bundled VLC lookup fails'
)
provides=('simpmusic')
conflicts=('simpmusic')
source=("${asset_name}::https://github.com/CamelliaV/SimpMusic/releases/download/${release_tag}/${asset_name}")
sha256sums=('${sha256}')

EOF

cat <<'EOF'
package() {
  local app_src="${srcdir}/SimpMusic-linux-x86_64"
  local app_dst="${pkgdir}/opt/simpmusic"

  if [[ ! -d "${app_src}" ]]; then
    echo "Expected extracted app tree at ${app_src}" >&2
    exit 1
  fi

  install -dm755 "${app_dst}"
  cp -a "${app_src}/." "${app_dst}/"
  chmod -R u+rwX,go+rX "${app_dst}"

  if [[ -d "${app_dst}/bin" ]]; then
    find "${app_dst}/bin" -type f -exec chmod 755 {} +
  fi

  install -Dm755 /dev/stdin "${pkgdir}/usr/bin/simpmusic" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail

app_home="/opt/simpmusic"
app_dir="${app_home}/lib/app"
cfg_file="${app_dir}/SimpMusic.cfg"
vlc_path="${app_dir}/vlc"

export SKIKO_RENDER_API="${SKIKO_RENDER_API:-OPENGL}"
export __GL_SYNC_TO_VBLANK="${__GL_SYNC_TO_VBLANK:-1}"

if [[ -z "${__GLX_VENDOR_LIBRARY_NAME:-}" && -d /proc/driver/nvidia ]]; then
  export __GLX_VENDOR_LIBRARY_NAME=nvidia
fi

if [[ -n "${SIMPMUSIC_JAVA_BIN:-}" ]]; then
  java_bin="${SIMPMUSIC_JAVA_BIN}"
elif [[ -x /usr/lib/jvm/default-runtime/bin/java ]]; then
  java_bin="/usr/lib/jvm/default-runtime/bin/java"
elif [[ -x /usr/lib/jvm/default/bin/java ]]; then
  java_bin="/usr/lib/jvm/default/bin/java"
else
  java_bin="$(command -v java)"
fi

if [[ ! -f "${cfg_file}" ]]; then
  exec "${app_home}/bin/simpmusic" "$@"
fi

classpath_entries=()
while IFS= read -r line; do
  case "${line}" in
    app.classpath=*)
      entry="${line#app.classpath=}"
      classpath_entries+=("${entry//\$APPDIR/${app_dir}}")
      ;;
  esac
done < "${cfg_file}"

if [[ "${#classpath_entries[@]}" -eq 0 ]]; then
  while IFS= read -r jar; do
    classpath_entries+=("${jar}")
  done < <(find "${app_dir}" -maxdepth 1 -name '*.jar' -print | sort)
fi

if [[ "${#classpath_entries[@]}" -eq 0 ]]; then
  exec "${app_home}/bin/simpmusic" "$@"
fi

IFS=:
classpath="${classpath_entries[*]}"
unset IFS

exec "${java_bin}" \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  -Dvlc.bundled.path="${vlc_path}" \
  -Dcompose.application.resources.dir="${app_dir}/resources" \
  -Dcompose.application.configure.swing.globals=true \
  -Dskiko.library.path="${app_dir}" \
  -Dskiko.renderApi=OPENGL \
  -Dskiko.vsync.enabled=true \
  -Dskiko.vsync.framelimit.fallback.enabled=true \
  -Dskiko.rendering.linux.waitForFrameVsyncOnRedrawImmediately=true \
  -cp "${classpath}" \
  com.maxrave.simpmusic.MainKt \
  "$@"
LAUNCHER

  install -Dm644 /dev/stdin "${pkgdir}/usr/share/applications/simpmusic.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Version=1.0
Name=SimpMusic
Comment=SimpMusic CamelliaV fork
Exec=simpmusic %u
Icon=simpmusic
Terminal=false
Categories=Audio;AudioVideo;Player;
StartupWMClass=com-maxrave-simpmusic-MainKt
MimeType=x-scheme-handler/simpmusic;
DESKTOP

  local icon_src=""
  for candidate in \
    "${app_dst}/simpmusic.png" \
    "${app_dst}/lib/app/simpmusic.png" \
    "${app_dst}/lib/app/resources/simpmusic.png" \
    "${app_dst}/lib/app/resources/circle_app_icon.png"; do
    if [[ -f "${candidate}" ]]; then
      icon_src="${candidate}"
      break
    fi
  done

  if [[ -n "${icon_src}" ]]; then
    install -Dm644 "${icon_src}" "${pkgdir}/usr/share/icons/hicolor/256x256/apps/simpmusic.png"
  fi
}
EOF
