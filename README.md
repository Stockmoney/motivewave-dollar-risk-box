# PL $ 1 Target for MotiveWave

**English** | [Русский](README.ru.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
![Display only](https://img.shields.io/badge/orders-never%20sent-blue)

A risk/reward box for [MotiveWave](https://www.motivewave.com) that shows **money, not points** — and tells you **how many contracts** fit your risk.

MotiveWave's built-in *PL 1 Target* drawing tool labels profit and loss as *points × quantity*. This indicator looks and behaves like it, but every label is in **dollars**, and the quantity is **calculated from your risk** (a fixed $ amount or a % of your balance).

![Overview](docs/overview.jpg)

> **Display only.** The indicator is a plain MotiveWave *Study*. It has no access to the order API and **cannot place, modify or cancel orders**. It only draws and calculates.

## Features

- **Stop / Entry / Target box** in the style of the built-in tool, with labels:
  `S:` stop price, loss in $ and % of balance · `E:` entry price, current P/L in $, R/R, contracts `Q`, balance · `T:` target price, profit in $ and %.
- **Position size from risk.** Fixed $ risk *or* % of balance. Contracts = `floor(risk ÷ (stop distance × point value))`. Optional fixed quantity.
- **Long / Short panel on the chart.** Press a button, click the chart — a box appears right there. Place as many boxes as you like.
- **Move the whole box** by grabbing it anywhere (fill or labels), like in TradingView. Drag the handles to change stop, target or width.
- **Draggable panel** — grab the `⠿` grip and put the panel wherever you like; the position is saved.
- Per-box **✕** button, right-click menu (delete, flip long/short, delete all).
- Colors, lines, font, text alignment and what to show are configurable on the *Format* tab.

![Panel](docs/panel.jpg)

## Install

1. Download `PLDollarTarget.jar` from the [latest release](../../releases/latest).
2. Put it into the **`MotiveWave Extensions`** folder in your user home folder (MotiveWave scans it automatically; on macOS: `~/MotiveWave Extensions` — create the folder if it does not exist).
3. Restart MotiveWave. The indicator appears under **Study → General → PL $ 1 Target**.

Add it to a chart once — the **Long / Short** panel appears. Save the chart as a *Template* if you want the panel on every chart.

> Tested on macOS with MotiveWave 7.1.1 and CME futures (MNQ). The jar is plain Java and should work on Windows too, but that has not been tested.

## How to use

| You want to… | Do this |
|---|---|
| Plan a long | Press **Long target**, click the chart where your entry is |
| Plan a short | Press **Short target**, click the chart |
| Move the whole box | Press and drag it anywhere inside (fill or label) |
| Change stop / target | Click the box once, drag the dot on the stop or target line |
| Change the width | Drag the dot at the right end of the entry line |
| Delete one box | Click its **✕**, or right-click it → *Delete this box* |
| Flip long ↔ short | Right-click the box → *Flip* |
| Move the panel | Drag the `⠿` grip |

New boxes start one average-bar range from the entry (stop) and twice that (target), i.e. 2R. Adjust them as you like.

### Settings

![General](docs/settings-general.jpg)

- **Risk Type** — *Fixed Amount* uses **Risk ($)**; *Percent of Balance* uses **Risk (%)** × **Account Balance**.
- **Account Balance** is typed in by hand. A MotiveWave indicator cannot read your broker account, so update it when your balance changes.
- **Fixed Quantity** — switch off the risk-based sizing and use a constant number of contracts.

![Format](docs/settings-format.jpg)

### Example

MNQ, point value $2. Stop 46 points away → $92 risk per contract. With a $200 risk budget: `floor(200 / 92) = 2` contracts, maximum loss `2 × $92 = $184`.

### Protect the panel from accidental deletion

The boxes and the panel live inside one indicator. MotiveWave's trash icon (and *Delete* in the menu) removes the **whole indicator**, panel included. To prevent that, right-click the panel → **Lock Figure**. A locked indicator ignores the trash icon, while everything else keeps working. To remove it on purpose: *Unlock Figure*, then the trash icon.

## Build from source

Requirements: a JDK (17 or newer) and `mwave_sdk.jar` from your own MotiveWave installation (it is not included in this repository).

```bash
bash build.sh            # builds build/PLDollarTarget.jar
bash build.sh install    # also copies it into ~/MotiveWave Extensions
```

Different locations: `MW_SDK=/path/to/mwave_sdk.jar MW_EXT=/path/to/extensions bash build.sh install`.
On Windows, compile by hand: `javac --release 21 -cp mwave_sdk.jar -d out pl_dollar/PLDollarTarget.java`, copy `pl_dollar/nls/strings.properties` to `out/pl_dollar/nls/`, then `jar cf PLDollarTarget.jar -C out .`.

You can check the "no orders" claim yourself: `javap -v` on the compiled classes shows no reference to `order_mgmt` or `OrderContext`.

## Limitations

- The balance for % risk is entered manually (see above).
- Handles are drawn only while the indicator is selected (click a box) — a MotiveWave rule for indicators.
- All boxes belong to one indicator instance; deleting the indicator deletes them all (use *Lock Figure*).

## Disclaimer

This is an independent, community project. It is **not affiliated with, endorsed by or supported by MotiveWave**; "MotiveWave" and "PL 1 Target" belong to their respective owners. The indicator is a calculation aid, **not trading or financial advice**. Markets carry risk — verify every number yourself before you trade.

## License

[MIT](LICENSE) © 2026 Zen Trader
