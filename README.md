# Seeds of SPD

<img src="artwork/launcher-preview.png" alt="Seeds of SPD 应用图标" width="96" height="96" />

SoSPD是一款用于[Shattered Pixel Dungeon](https://github.com/00-evan/shattered-pixel-dungeon)的中文查种器，仅提供Android版本，基于Material3开发。部分代码参考来自[shpd-toolkit](https://github.com/Elektrochecker/shpd-toolkit)。

项目面向中文玩家群体，仅支持**简体中文**。

*兼容测试范围Android 10+，过旧的硬件或者软件可能不能正常运行。*



<table>
  <tr>
    <th>种子探查</th>
    <th>条件找种</th>
    <th>快速探查</th>
  </tr>
  <tr>
    <td><img src="artwork/screenshots/find.jpg" alt="逐层查看物品、来源与房间" width="240" /></td>
    <td><img src="artwork/screenshots/search.jpg" alt="选择物品条件、强化等级及匹配方式" width="240" /></td>
    <td><img src="artwork/screenshots/quick.jpg" alt="游戏画面中的快速探查悬浮窗" width="240" /></td>
  </tr>
</table>

## 目录说明

| 路径 | 用途 |
| --- | --- |
| `app/` | 原生界面、后台搜索与快速探查悬浮窗 |
| `domain/` | 种子编码、条件匹配与搜索分段模型 |
| `engine/` | 上游生成逻辑的运行适配 |
| `verification/` | 与官方游戏的生成对照代码 |
| `vendor/shpd/` | 固定版本的上游源码 |
| `artwork/screenshots/` | README 使用的界面截图 |
| `artwork/upstream/` | 游戏官方图标素材 |
| `tools/` | 图标生成与发布打包脚本 |

本机 SDK、状态和验证记录保存在 `.local/`，其中记录文档位于 `.local/notes/`；安装包位于 `artifacts/`。这些文件不纳入 Git。`.signing/` 和 `signing/` 中的签名材料也仅保留在本地，维护原签名发布包时需单独备份和恢复。

## 来源与许可证

- SHPD Toolkit，作者 Elektrochecker；上游固定提交为 `a209643e6e91247166f439da8aba56d6f03a659e`。
- Shattered Pixel Dungeon，作者 Evan Debenham；其原作 Pixel Dungeon 的作者为 Oleg Dolya。
- 图标中的下水道地板、水晶钥匙及物品素材来自官方游戏 `4.0.2`，素材来源提交为 `57a4e06a4caf162446d1c28caa7983f0493fecf0`。

上游源码保留原始版权标识。本项目采用 **GPL-3.0-or-later**，许可证全文见 [LICENSE](LICENSE)。
