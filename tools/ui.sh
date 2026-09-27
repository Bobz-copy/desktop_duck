#!/usr/bin/env bash
# Ayudas para manejar la app por adb en pruebas manuales.
#   tools/ui.sh tap <id>        toca la vista con ese resource-id (sin el prefijo del paquete)
#   tools/ui.sh type <id> <txt> toca la vista y escribe el texto
#   tools/ui.sh text <id>       imprime el texto de la vista
#   tools/ui.sh shot <archivo>  guarda una captura de pantalla
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-/c/Android/sdk/platform-tools/adb.exe}"
PKG=com.cfks.goosedroid

node_for() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" shell cat /sdcard/ui.xml | grep -o "<node[^>]*resource-id=\"$PKG:id/$1\"[^>]*>" | head -1
}

center_of() {
  local node bounds
  node=$(node_for "$1")
  [ -z "$node" ] && { echo "no se encontró la vista $1" >&2; return 1; }
  bounds=$(echo "$node" | grep -o 'bounds="[^"]*"' | tr -c '0-9' ' ')
  read -r x1 y1 x2 y2 <<<"$bounds"
  echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

case "${1:-}" in
  tap)
    pos=$(center_of "$2") || exit 1
    "$ADB" shell input tap $pos
    ;;
  type)
    pos=$(center_of "$2") || exit 1
    "$ADB" shell input tap $pos
    sleep 0.5
    "$ADB" shell input text "$(echo "$3" | sed 's/ /%s/g')"
    ;;
  text)
    node_for "$2" | grep -o ' text="[^"]*"' | head -1 | sed 's/^ text="//; s/"$//'
    ;;
  shot)
    "$ADB" exec-out screencap -p > "$2"
    ;;
  *)
    echo "uso: $0 tap|type|text|shot ..." >&2
    exit 2
    ;;
esac
