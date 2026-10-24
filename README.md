# 多时闹钟（MultiTimeAlarm）

[![Release](https://img.shields.io/github/v/release/sundys/MultiTimeAlarm?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/sundys/MultiTimeAlarm/releases)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![开发文档](https://img.shields.io/badge/%E5%BC%80%E5%8F%91%E6%96%87%E6%A1%A3-docs-green.svg)](docs/开发文档.md)

一款 Android 闹钟应用，核心特色是**一个定时任务可设置多个提醒时间**，专为需要"按日程多点提醒"的场景设计（服药、打卡、会议前多次提醒等）。

## 功能特性

### 核心特色
- **一个闹钟，多个时间点**：一个任务可添加任意多个提醒时间，统一管理、可单独启停
- **五种提醒类型**：
  - 每天重复：如吃药 8:00 / 12:00 / 18:00
  - 按星期：周一至周日任意组合（工作日 / 周末 / 每天快捷预设）
  - 每月：可指定多个日期（如 1,3,9,25 或 1-15），也可勾选「最后一天」按每月实际天数提醒
  - 间隔循环：每 N 分钟 / 小时 / 天 / 月循环提醒，天与月按日历计算（月份天数不固定）
  - 仅一次：指定日期多点提醒，全部触发后自动关闭
- **时间精确到秒**：时/分/秒三列数字滚动选择器
- **小憩倒计时**：一键"N 分钟后响铃"

### 提醒可靠性
- `AlarmManager.setAlarmClock()` 精确调度：免"闹钟和提醒"特殊权限，Doze 休眠下准时触发
- 高优先级通知 + 全屏 Intent，锁屏直接弹出响铃页（铃声 + 振动）
- 贪睡可配置：间隔（分钟）与次数上限（0 = 不限）
- 重启后开机广播自动恢复全部闹钟；进程被杀有冷启动兜底重调度
- 设置页提供"忽略电池优化"与"自启动设置"入口，适配 MIUI/EMUI 等国产 ROM

## 首次使用授权说明

- **通知权限**（Android 13+）：首次启动自动发起授权请求
- 调度不需要"闹钟和提醒"特殊权限，安装即可用
- 建议在 设置 → 忽略电池优化 中授权，并在自启动设置中允许本应用，避免国产 ROM 后台管控拦截闹钟

## 开源许可

本项目以 **GNU Affero 通用公共许可证第 3 版（AGPL-3.0）** 发布。

```
MultiTimeAlarm
Copyright (C) 2026 sundys

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

- 完整协议文本：[`LICENSE`](LICENSE)（与官方原文逐字一致，未经修改）
- 协议全文在线版：<https://www.gnu.org/licenses/agpl-3.0.html>
- 分发修改版或通过网络向用户提供本软件服务时，须按 AGPL-3.0 第 13 条向使用者提供对应的完整源代码

## 社区支持

欢迎加入 Linux.do 社区 交流反馈：[linux.do](https://linux.do)
