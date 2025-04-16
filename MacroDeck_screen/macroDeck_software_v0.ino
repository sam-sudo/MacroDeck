#include <TFT_eSPI.h>
#include <SPI.h>
#include <string.h>
#include "Adafruit_TinyUSB.h"

// Calibración táctil
#define TOUCH_MIN_X 200
#define TOUCH_MAX_X 3912
#define TOUCH_MIN_Y 186
#define TOUCH_MAX_Y 3918

#define TFT_MIN_X 0
#define TFT_MAX_X 479
#define TFT_MIN_Y 0
#define TFT_MAX_Y 319

TFT_eSPI tft = TFT_eSPI();
TFT_eSPI_Button btn1, btn2;
Adafruit_USBD_HID usb_hid;

const int btnWidth = 180;
const int btnHeight = 80;
const int btnY = 160;
const int btn1X = 120;
const int btn2X = 360;

bool wasTouched = false;
bool alreadyFired = false;

// Dibujar botón táctil
void drawButton(TFT_eSPI_Button& btn, const char* label, int x, int y, uint16_t color) {
  char labelText[20];
  strcpy(labelText, label);
  btn.initButton(&tft, x, y, btnWidth, btnHeight,
                 TFT_WHITE, color, TFT_WHITE, labelText, 2);
  btn.drawButton();
}

// Efecto visual al tocar el botón
void buttonEffect(TFT_eSPI_Button& btn, const char* label, int x, int y, uint16_t color) {
  drawButton(btn, label, x, y, TFT_DARKGREY);
  delay(100);
  drawButton(btn, label, x, y, color);
}

// Enviar tecla HID al PC
void sendKeyCombo(uint8_t keycode) {
  if (!usb_hid.ready()) return;

  // Reporte de pulsación (8 bytes)
  uint8_t report[8] = { 0 };
  report[2] = keycode;  // Posición de la tecla en el arreglo
  usb_hid.sendReport(0, report, sizeof(report));

  delay(10);  // tiempo corto, como un toque real

  // Reporte de liberación
  memset(report, 0, sizeof(report));
  usb_hid.sendReport(0, report, sizeof(report));
}


// Descriptor HID
uint8_t const desc_hid_report[] = {
  TUD_HID_REPORT_DESC_KEYBOARD()
};

void setup() {
  Serial.begin(115200);

  usb_hid.setReportDescriptor(desc_hid_report, sizeof(desc_hid_report));
  usb_hid.setPollInterval(2);
  usb_hid.begin();

  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);

  uint16_t calData[5] = { TOUCH_MIN_X, TOUCH_MAX_X, TOUCH_MIN_Y, TOUCH_MAX_Y, 7 };
  tft.setTouch(calData);

  drawButton(btn1, "Chrome", btn1X, btnY, TFT_RED);
  drawButton(btn2, "Nueva Pest.", btn2X, btnY, TFT_BLUE);
}

void loop() {
  uint16_t x, y;
  bool touched = tft.getTouch(&x, &y);

  if (touched) {
    if (!wasTouched) {
      // Primer toque (TOUCH DOWN)
      wasTouched = true;

      if (btn1.contains(x, y)) {
        sendKeyCombo(HID_KEY_F13);
        buttonEffect(btn1, "Chrome", btn1X, btnY, TFT_RED);
        alreadyFired = true;
      }

      if (btn2.contains(x, y)) {
        sendKeyCombo(HID_KEY_A);
        buttonEffect(btn2, "Nueva Pest.", btn2X, btnY, TFT_BLUE);
        alreadyFired = true;
      }
    }
  } else {
    // TOUCH UP (levantó el dedo)
    wasTouched = false;
    alreadyFired = false;
  }
}
