"""
经期记录App - 月经周期跟踪器 (优化版)
一个美观易用的经期记录应用程序
包含：智能预测算法、统计图表、提醒功能、优化的日历视图
"""

# ************* 字体配置修复 *************
import os
import platform
# 改进前
font_files = [
    'C:/Windows/Fonts/msyh.ttc',
    'C:/Windows/Fonts/msyh.ttf',
    # 其他Windows路径
]

# 改进后
import platform
def get_system_fonts():
    """获取系统字体路径，跨平台兼容"""
    if platform.system() == 'Windows':
        return ['C:/Windows/Fonts/msyh.ttc', 'C:/Windows/Fonts/simhei.ttf']
    elif platform.system() == 'Darwin':  # macOS
        return ['/Library/Fonts/Songti.ttc', '/Library/Fonts/STHeiti Light.ttc']
    else:  # Linux
        return ['/usr/share/fonts/truetype/wqy/wqy-microhei.ttc']
font_files = get_system_fonts()

available_font = None
for font_path in font_files:
    if os.path.exists(font_path):
        available_font = font_path
        print(f"找到可用字体: {available_font}")
        break

if available_font:
    os.environ['KIVY_FONTS'] = os.path.dirname(available_font)
    os.environ['KIVY_DEFAULT_FONT'] = available_font
    
    from kivy.config import Config
    Config.set('kivy', 'default_font', [
        available_font,
        available_font,
        available_font,
        available_font,
        available_font
    ])
    print(f"已设置默认字体为: {available_font}")
else:
    print("警告: 未找到系统字体文件!")
# *****************************************

import json
import os
import math
import random
from datetime import datetime, timedelta
from collections import defaultdict, deque
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.uix.togglebutton import ToggleButton
from kivy.uix.popup import Popup
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.core.window import Window
from kivy.graphics import Color, RoundedRectangle, Line, Ellipse, Rectangle
from kivy.graphics import InstructionGroup
from kivy.metrics import dp, sp
from kivy.clock import Clock
from kivy.properties import StringProperty, ListProperty, NumericProperty, BooleanProperty, ObjectProperty
from kivy.uix.behaviors import ButtonBehavior
from kivy.uix.image import Image
from kivy.uix.widget import Widget
import calendar as py_calendar
import numpy as np

# 设置窗口大小
Window.size = (400, 700)
Window.clearcolor = (0.98, 0.96, 0.97, 1)  # 更浅的粉色背景

# ============================================
# 自定义控件类
# ============================================

class RoundedButton(ButtonBehavior, BoxLayout):
    text = StringProperty('')
    background_color = ListProperty([0.93, 0.6, 0.73, 1])
    text_color = ListProperty([1, 1, 1, 1])
    radius = ListProperty([dp(12)])
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint = (None, None)
        self.height = dp(45)
        self.width = dp(140)
        
        with self.canvas.before:
            Color(*self.background_color)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=self.radius)
            
        self.bind(pos=self.update_rect, size=self.update_rect)
        
        self.label = Label(text=self.text, color=self.text_color, 
                          font_size=sp(15), bold=True, font_name=available_font)
        self.add_widget(self.label)
    
    def update_rect(self, *args):
        self.rect.pos = self.pos
        self.rect.size = self.size

class PrettyLabel(Label):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.color = (0.35, 0.18, 0.25, 1)  # 更深的粉色
        self.font_size = sp(15)
        self.halign = 'left'
        self.valign = 'middle'
        self.size_hint_y = None
        self.height = dp(40)
        self.padding_x = dp(12)
        self.font_name = available_font

class PrettyTextInput(TextInput):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.background_color = (1, 1, 1, 0.9)
        self.foreground_color = (0.2, 0.2, 0.2, 1)
        self.font_size = sp(15)
        self.size_hint_y = None
        self.height = dp(40)
        self.padding_x = dp(12)
        self.padding_y = dp(10)
        self.multiline = False
        self.font_name = 'simhei'
        
        with self.canvas.before:
            Color(0.93, 0.8, 0.85, 0.8)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(8)])
        
        self.bind(pos=self.update_rect, size=self.update_rect)
    
    def update_rect(self, *args):
        self.rect.pos = (self.pos[0]-dp(4), self.pos[1]-dp(4))
        self.rect.size = (self.size[0]+dp(8), self.size[1]+dp(8))

# ============================================
# 智能预测算法类
# ============================================

class CyclePredictor:
    """智能周期预测算法"""
    
    def __init__(self, records):
        self.records = records
        self.period_starts = self.extract_period_starts()
        
    def extract_period_starts(self):
        """提取所有经期开始日期"""
        starts = []
        for record in self.records:
            if record.get('type') == 'period':
                start_date_str = record.get('start_date')
                if start_date_str:
                    try:
                        start_date = datetime.strptime(start_date_str, '%Y-%m-%d')
                        starts.append(start_date)
                    except ValueError:
                        continue
        return sorted(starts)
    
    def calculate_weighted_average_cycle(self, n_recent=6):
        """计算加权平均周期长度（最近的数据权重更高）"""
        if len(self.period_starts) < 2:
            return 28  # 默认周期
        
        cycle_lengths = []
        for i in range(1, len(self.period_starts)):
            days_diff = (self.period_starts[i] - self.period_starts[i-1]).days
            if 20 <= days_diff <= 45:  # 合理的周期范围
                cycle_lengths.append((days_diff, i))  # 保存周期长度和索引
        
        if not cycle_lengths:
            return 28
        
        # 计算权重：最近的数据权重更高
        weights = []
        values = []
        
        for length, idx in cycle_lengths[-n_recent:]:  # 只考虑最近的n个周期
            weight = (idx / len(self.period_starts)) * 2 + 0.5  # 最近的数据权重更高
            weights.append(weight)
            values.append(length)
        
        # 加权平均
        weighted_sum = sum(w * v for w, v in zip(weights, values))
        total_weight = sum(weights)
        
        return weighted_sum / total_weight if total_weight > 0 else 28
    
    def predict_next_period(self):
        """预测下一个经期"""
        if len(self.period_starts) < 2:
            return None, None, None, None
        
        avg_cycle = self.calculate_weighted_average_cycle()
        last_period_start = self.period_starts[-1]
        
        # 预测下一个经期开始日期
        next_period_start = last_period_start + timedelta(days=avg_cycle)
        
        # 预测排卵期（基于黄体期通常为14天）
        ovulation_date = next_period_start - timedelta(days=14)
        
        # 预测易孕期（排卵期前后几天）
        fertile_start = ovulation_date - timedelta(days=5)
        fertile_end = ovulation_date + timedelta(days=1)
        
        # 预测经期结束日期（基于历史平均经期长度）
        avg_period_length = self.calculate_avg_period_length()
        next_period_end = next_period_start + timedelta(days=avg_period_length - 1)
        
        return next_period_start, next_period_end, ovulation_date, (fertile_start, fertile_end)
    
    def calculate_avg_period_length(self):
        """计算平均经期长度"""
        lengths = []
        for record in self.records:
            if record.get('type') == 'period':
                start_str = record.get('start_date')
                end_str = record.get('end_date') or start_str
                if start_str and end_str:
                    try:
                        start = datetime.strptime(start_str, '%Y-%m-%d')
                        end = datetime.strptime(end_str, '%Y-%m-%d')
                        length = (end - start).days + 1
                        if 2 <= length <= 10:  # 合理的经期长度范围
                            lengths.append(length)
                    except ValueError:
                        continue
        
        return sum(lengths) / len(lengths) if lengths else 5
    
    def get_cycle_statistics(self):
        """获取周期统计数据"""
        if len(self.period_starts) < 2:
            return {}
        
        # 计算周期长度
        cycle_lengths = []
        for i in range(1, len(self.period_starts)):
            days_diff = (self.period_starts[i] - self.period_starts[i-1]).days
            if 20 <= days_diff <= 45:
                cycle_lengths.append(days_diff)
        
        if not cycle_lengths:
            return {}
        
        # 计算统计数据
        stats = {
            'avg_cycle': sum(cycle_lengths) / len(cycle_lengths),
            'min_cycle': min(cycle_lengths),
            'max_cycle': max(cycle_lengths),
            'std_cycle': np.std(cycle_lengths) if len(cycle_lengths) > 1 else 0,
            'cycle_count': len(cycle_lengths),
            'cycle_lengths': cycle_lengths,
            'irregularity': self.calculate_irregularity_score(cycle_lengths)
        }
        
        return stats
    
    def calculate_irregularity_score(self, cycle_lengths):
        """计算周期不规律性评分（0-100，越高越不规律）"""
        if len(cycle_lengths) < 3:
            return 0
        
        # 计算相邻周期差异
        diffs = []
        for i in range(1, len(cycle_lengths)):
            diffs.append(abs(cycle_lengths[i] - cycle_lengths[i-1]))
        
        avg_diff = sum(diffs) / len(diffs)
        max_possible_diff = 25  # 最大可能的周期差异
        
        # 将平均差异转换为0-100的评分
        score = min(100, (avg_diff / max_possible_diff) * 100)
        return round(score, 1)

# ============================================
# 统计图表类
# ============================================

class CycleChart(Widget):
    """周期长度折线图"""
    
    def __init__(self, cycle_lengths, **kwargs):
        super().__init__(**kwargs)
        self.cycle_lengths = cycle_lengths
        self.size_hint = (1, 1)
        self.bind(pos=self.draw_chart, size=self.draw_chart)
    
    def draw_chart(self, *args):
        self.canvas.clear()
        
        if not self.cycle_lengths or len(self.cycle_lengths) < 2:
            with self.canvas:
                Color(0.7, 0.7, 0.7, 1)
                Label(text='需要更多数据', pos=self.pos, size=self.size)
            return
        
        # 计算图表参数
        x_margin = dp(40)
        y_margin = dp(30)
        chart_width = self.width - 2 * x_margin
        chart_height = self.height - 2 * y_margin
        
        if chart_width <= 0 or chart_height <= 0:
            return
        
        # 数据范围
        min_val = min(self.cycle_lengths)
        max_val = max(self.cycle_lengths)
        val_range = max_val - min_val
        
        # 绘制坐标轴
        with self.canvas:
            Color(0.5, 0.5, 0.5, 0.8)
            
            # X轴
            Line(points=[
                self.x + x_margin, self.y + y_margin,
                self.x + x_margin + chart_width, self.y + y_margin
            ], width=1.5)
            
            # Y轴
            Line(points=[
                self.x + x_margin, self.y + y_margin,
                self.x + x_margin, self.y + y_margin + chart_height
            ], width=1.5)
            
            # 网格线
            Color(0.8, 0.8, 0.8, 0.3)
            # 水平网格线
            for i in range(5):
                y = self.y + y_margin + (i * chart_height / 4)
                Line(points=[
                    self.x + x_margin, y,
                    self.x + x_margin + chart_width, y
                ], width=1)
            
            # 绘制折线
            if len(self.cycle_lengths) > 1:
                points = []
                for i, val in enumerate(self.cycle_lengths):
                    x = self.x + x_margin + (i * chart_width / (len(self.cycle_lengths) - 1))
                    y = self.y + y_margin + ((val - min_val) / val_range * chart_height) if val_range > 0 else self.y + y_margin
                    points.extend([x, y])
                
                Color(0.93, 0.6, 0.73, 1)
                Line(points=points, width=2.5)
                
                # 绘制数据点
                for i, val in enumerate(self.cycle_lengths):
                    x = self.x + x_margin + (i * chart_width / (len(self.cycle_lengths) - 1))
                    y = self.y + y_margin + ((val - min_val) / val_range * chart_height) if val_range > 0 else self.y + y_margin
                    
                    Color(0.93, 0.6, 0.73, 1)
                    Ellipse(pos=(x-dp(3), y-dp(3)), size=(dp(6), dp(6)))
                    
                    # 显示数值
                    Color(0.4, 0.2, 0.3, 1)
                    Label(text=str(val), font_size=sp(10),
                          pos=(x-dp(8), y+dp(5)), size=(dp(16), dp(16)))
            
            # 添加坐标轴标签
            Color(0.4, 0.2, 0.3, 1)
            # X轴标签
            for i in range(len(self.cycle_lengths)):
                x = self.x + x_margin + (i * chart_width / max(1, len(self.cycle_lengths) - 1))
                Label(text=f"第{i+1}次", font_size=sp(10),
                      pos=(x-dp(10), self.y + y_margin - dp(20)), size=(dp(20), dp(15)))
            
            # Y轴标签
            for i in range(5):
                y = self.y + y_margin + (i * chart_height / 4)
                val = min_val + (i * val_range / 4)
                Label(text=f"{int(val)}天", font_size=sp(10),
                      pos=(self.x + x_margin - dp(25), y-dp(8)), size=(dp(25), dp(16)))

class SymptomChart(Widget):
    """症状频率饼图"""
    
    def __init__(self, symptom_data, **kwargs):
        super().__init__(**kwargs)
        self.symptom_data = symptom_data
        self.size_hint = (1, 1)
        self.bind(pos=self.draw_chart, size=self.draw_chart)
    
    def draw_chart(self, *args):
        self.canvas.clear()
        
        if not self.symptom_data:
            with self.canvas:
                Color(0.7, 0.7, 0.7, 1)
                Label(text='暂无症状数据', pos=self.pos, size=self.size)
            return
        
        # 颜色定义
        colors = [
            (0.93, 0.6, 0.73, 1),   # 粉色
            (0.6, 0.8, 0.6, 1),     # 绿色
            (0.8, 0.8, 0.4, 1),     # 黄色
            (0.6, 0.7, 0.9, 1),     # 蓝色
            (0.8, 0.6, 0.8, 1),     # 紫色
            (0.9, 0.7, 0.5, 1),     # 橙色
        ]
        
        total = sum(self.symptom_data.values())
        if total == 0:
            return
        
        # 计算圆心和半径
        center_x = self.center_x
        center_y = self.center_y
        radius = min(self.width, self.height) * 0.35
        
        # 绘制饼图
        start_angle = 0
        with self.canvas:
            for i, (symptom, count) in enumerate(self.symptom_data.items()):
                if count == 0:
                    continue
                    
                # 计算扇区角度
                angle = 360 * (count / total)
                
                # 绘制扇区
                color_idx = i % len(colors)
                Color(*colors[color_idx])
                
                # 使用多个小线段模拟扇区
                segment_count = int(angle * 2)
                if segment_count < 1:
                    continue
                    
                segment_angle = angle / segment_count
                
                points = [center_x, center_y]
                for seg in range(segment_count + 1):
                    current_angle = start_angle + (seg * segment_angle)
                    rad = math.radians(current_angle)
                    x = center_x + radius * math.cos(rad)
                    y = center_y + radius * math.sin(rad)
                    points.extend([x, y])
                
                # 闭合图形
                points.extend([center_x, center_y])
                
                # 绘制多边形
                Line(points=points, width=1, close=True)
                
                # 绘制图例
                legend_x = self.x + dp(20)
                legend_y = self.y + self.height - dp(30) - (i * dp(25))
                
                Color(*colors[color_idx])
                Rectangle(pos=(legend_x, legend_y), size=(dp(15), dp(15)))
                
                Color(0.4, 0.2, 0.3, 1)
                Label(text=f"{symptom}: {count}次", font_size=sp(11),
                      pos=(legend_x + dp(20), legend_y-dp(3)), size=(dp(120), dp(20)))
                
                start_angle += angle

# ============================================
# 优化的日历视图
# ============================================

class CalendarDayButton(Button):
    """日历日期按钮"""
    
    def __init__(self, date, has_period=False, has_mood=False, has_intimacy=False, **kwargs):
        super().__init__(**kwargs)
        self.date = date
        self.has_period = has_period
        self.has_mood = has_mood
        self.has_intimacy = has_intimacy
        self.background_normal = ''
        self.background_color = (0.95, 0.95, 0.95, 1) if date else (0.9, 0.9, 0.9, 0.5)
        self.color = (0.3, 0.2, 0.25, 1)
        self.font_size = sp(14)
        self.font_name = 'simhei'
        self.bold = True
        
        # 如果有记录，添加指示器
        self.indicators = []
        self.create_indicators()
    
    def create_indicators(self):
        """创建记录指示器"""
        indicator_size = dp(6)
        spacing = dp(2)
        
        if self.has_period:
            with self.canvas.after:
                Color(0.93, 0.6, 0.73, 1)  # 粉色
                self.indicators.append(
                    Ellipse(pos=(self.center_x - indicator_size/2, self.y + spacing),
                           size=(indicator_size, indicator_size))
                )
        
        if self.has_mood:
            with self.canvas.after:
                Color(0.8, 0.8, 0.4, 1)  # 黄色
                x_offset = len(self.indicators) * (indicator_size + spacing)
                self.indicators.append(
                    Ellipse(pos=(self.center_x - indicator_size/2 + x_offset, self.y + spacing),
                           size=(indicator_size, indicator_size))
                )
        
        if self.has_intimacy:
            with self.canvas.after:
                Color(0.6, 0.8, 0.6, 1)  # 绿色
                x_offset = len(self.indicators) * (indicator_size + spacing)
                self.indicators.append(
                    Ellipse(pos=(self.center_x - indicator_size/2 + x_offset, self.y + spacing),
                           size=(indicator_size, indicator_size))
                )
    
    def on_size(self, *args):
        """当大小改变时更新指示器位置"""
        for indicator in self.indicators:
            indicator.pos = (self.center_x - dp(3), self.y + dp(2))

# ============================================
# 主日历屏幕
# ============================================

class MainCalendarScreen(Screen):
    """主日历屏幕 - 集成所有功能"""
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = 'main_calendar'
        
        # 主布局
        main_layout = BoxLayout(orientation='vertical', spacing=dp(5))
        
        # 顶部标题栏
        header = BoxLayout(orientation='horizontal', size_hint_y=0.12, 
                          padding=dp(10), spacing=dp(10))
        
        # 左侧：返回按钮和标题
        left_header = BoxLayout(orientation='horizontal', size_hint_x=0.6)
        
        self.month_label = Label(
            text='2026年1月',
            font_size=sp(22),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            font_name='simhei'
        )
        
        left_header.add_widget(self.month_label)
        
        # 右侧：功能按钮
        right_header = BoxLayout(orientation='horizontal', size_hint_x=0.4, spacing=dp(5))
        
        today_btn = Button(
            text='今天',
            size_hint=(0.5, 1),
            background_color=(0.93, 0.8, 0.85, 1),
            color=(0.93, 0.6, 0.73, 1),
            font_name='simhei'
        )
        today_btn.bind(on_press=self.go_to_today)
        
        add_btn = Button(
            text='+',
            size_hint=(0.3, 1),
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            bold=True,
            font_size=sp(20)
        )
        add_btn.bind(on_press=self.show_add_menu)
        
        right_header.add_widget(today_btn)
        right_header.add_widget(add_btn)
        
        header.add_widget(left_header)
        header.add_widget(right_header)
        
        # 月份导航
        nav_layout = BoxLayout(orientation='horizontal', size_hint_y=0.08, 
                              padding=(dp(20), 0, dp(20), 0))
        
        prev_btn = Button(
            text='◀',
            size_hint=(0.2, 1),
            background_color=(0.95, 0.95, 0.95, 1),
            color=(0.93, 0.6, 0.73, 1),
            font_size=sp(18)
        )
        prev_btn.bind(on_press=self.prev_month)
        
        year_month_label = Label(
            text='',
            font_size=sp(16),
            color=(0.5, 0.3, 0.4, 1),
            font_name='simhei'
        )
        
        next_btn = Button(
            text='▶',
            size_hint=(0.2, 1),
            background_color=(0.95, 0.95, 0.95, 1),
            color=(0.93, 0.6, 0.73, 1),
            font_size=sp(18)
        )
        next_btn.bind(on_press=self.next_month)
        
        nav_layout.add_widget(prev_btn)
        nav_layout.add_widget(year_month_label)
        nav_layout.add_widget(next_btn)
        
        self.year_month_label = year_month_label
        
        # 星期标题
        weekdays_layout = GridLayout(cols=7, size_hint_y=0.08, spacing=dp(2))
        weekdays = ['日', '一', '二', '三', '四', '五', '六']
        for day in weekdays:
            day_label = Label(
                text=day,
                font_size=sp(14),
                bold=True,
                color=(0.7, 0.5, 0.6, 1),
                font_name='simhei'
            )
            weekdays_layout.add_widget(day_label)
        
        # 日历网格
        self.calendar_grid = GridLayout(cols=7, spacing=dp(2), size_hint_y=0.6)
        
        # 底部功能区
        bottom_layout = BoxLayout(orientation='vertical', size_hint_y=0.12, 
                                 spacing=dp(5), padding=dp(10))
        
        # 状态显示
        self.status_label = Label(
            text='',
            font_size=sp(13),
            color=(0.6, 0.4, 0.5, 1),
            halign='center',
            font_name='simhei'
        )
        
        # 底部按钮
        button_layout = BoxLayout(orientation='horizontal', spacing=dp(10))
        
        buttons = [
            ('📊', 'stats', [0.93, 0.8, 0.85, 1]),
            ('🔔', 'reminders', [0.8, 0.9, 0.95, 1]),
            ('📈', 'charts', [0.95, 0.85, 0.9, 1]),
            ('⚙️', 'settings', [0.9, 0.9, 0.9, 1]),
        ]
        
        for icon, callback, color in buttons:
            btn = Button(
                text=icon,
                size_hint=(0.25, 1),
                background_color=color,
                color=(0.4, 0.2, 0.3, 1),
                font_size=sp(18)
            )
            btn.bind(on_press=lambda instance, cb=callback: self.show_bottom_sheet(cb))
            button_layout.add_widget(btn)
        
        bottom_layout.add_widget(self.status_label)
        bottom_layout.add_widget(button_layout)
        
        # 添加到主布局
        main_layout.add_widget(header)
        main_layout.add_widget(nav_layout)
        main_layout.add_widget(weekdays_layout)
        main_layout.add_widget(self.calendar_grid)
        main_layout.add_widget(bottom_layout)
        
        self.add_widget(main_layout)
        
        # 初始化当前日期
        self.current_date = datetime.now()
        self.update_calendar()
        self.update_status()
    
    def update_calendar(self):
        """更新日历显示"""
        # 清空日历网格
        self.calendar_grid.clear_widgets()
        
        year = self.current_date.year
        month = self.current_date.month
        
        # 更新标题
        self.month_label.text = f'{year}年{month}月'
        self.year_month_label.text = f'{year}年{month}月'
        
        # 获取月份信息
        first_day = datetime(year, month, 1)
        last_day = datetime(year, month + 1, 1) - timedelta(days=1) if month < 12 else datetime(year + 1, 1, 1) - timedelta(days=1)
        
        # 获取记录
        app = App.get_running_app()
        records = app.load_records()
        
        # 计算第一天是星期几 (0=周日, 6=周六)
        start_weekday = first_day.weekday()
        if start_weekday == 6:  # 如果第一天是周六，则从周日开始
            start_weekday = 0
        else:
            start_weekday += 1
        
        # 添加上个月的占位日期
        prev_month_last_day = datetime(year, month, 1) - timedelta(days=1)
        for i in range(start_weekday):
            day = prev_month_last_day.day - (start_weekday - i - 1)
            date = datetime(year, month - 1, day) if month > 1 else datetime(year - 1, 12, day)
            btn = CalendarDayButton(date=None)
            btn.text = ''
            self.calendar_grid.add_widget(btn)
        
        # 添加当前月的日期
        for day in range(1, last_day.day + 1):
            date = datetime(year, month, day)
            date_str = date.strftime('%Y-%m-%d')
            
            # 检查该日期的记录
            has_period = False
            has_mood = False
            has_intimacy = False
            
            for record in records:
                if record.get('type') == 'period':
                    start_date = record.get('start_date')
                    end_date = record.get('end_date') or start_date
                    if start_date and end_date:
                        try:
                            start = datetime.strptime(start_date, '%Y-%m-%d')
                            end = datetime.strptime(end_date, '%Y-%m-%d')
                            if start <= date <= end:
                                has_period = True
                        except:
                            pass
                
                elif record.get('type') == 'mood_symptom' and record.get('date') == date_str:
                    has_mood = True
                
                elif record.get('type') == 'intimacy' and record.get('date') == date_str:
                    has_intimacy = True
            
            # 创建日期按钮
            btn = CalendarDayButton(
                date=date,
                has_period=has_period,
                has_mood=has_mood,
                has_intimacy=has_intimacy
            )
            btn.text = str(day)
            
            # 如果是今天，特殊标记
            today = datetime.now()
            if date.year == today.year and date.month == today.month and date.day == today.day:
                btn.background_color = (0.93, 0.8, 0.85, 1)
            
            # 绑定点击事件
            btn.bind(on_press=lambda instance, d=date: self.on_date_click(d))
            
            self.calendar_grid.add_widget(btn)
        
        # 添加下个月的占位日期
        remaining_days = 42 - (start_weekday + last_day.day)  # 6x7网格
        for i in range(remaining_days):
            btn = CalendarDayButton(date=None)
            btn.text = ''
            self.calendar_grid.add_widget(btn)
    
    def on_date_click(self, date):
        """日期点击事件"""
        app = App.get_running_app()
        records = app.get_records_for_date(date)
        date_str = date.strftime('%Y-%m-%d')
        
        # 创建弹窗
        content = BoxLayout(orientation='vertical', spacing=dp(10), padding=dp(15))
        
        # 标题
        title = Label(
            text=f"{date.strftime('%Y年%m月%d日')} ({['一','二','三','四','五','六','日'][date.weekday()]})",
            font_size=sp(18),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            font_name='simhei'
        )
        content.add_widget(title)
        
        # 如果有记录，显示记录
        if records:
            records_layout = BoxLayout(orientation='vertical', spacing=dp(8))
            
            for record in records:
                if record.get('type') == 'period':
                    record_text = f"📅 经期记录"
                    records_layout.add_widget(Label(
                        text=record_text,
                        font_size=sp(14),
                        color=(0.93, 0.6, 0.73, 1),
                        font_name='simhei'
                    ))
                
                elif record.get('type') == 'mood_symptom':
                    mood = record.get('mood', '未知')
                    symptoms = record.get('symptoms', [])
                    symptoms_text = ', '.join(symptoms) if symptoms else '无'
                    record_text = f"😊 {mood}\n症状: {symptoms_text}"
                    records_layout.add_widget(Label(
                        text=record_text,
                        font_size=sp(14),
                        color=(0.8, 0.8, 0.4, 1),
                        font_name='simhei'
                    ))
                
                elif record.get('type') == 'intimacy':
                    intimacy_type = record.get('intimacy_type', '未知')
                    note = record.get('note', '')
                    record_text = f"💖 {intimacy_type}"
                    if note:
                        record_text += f"\n备注: {note}"
                    records_layout.add_widget(Label(
                        text=record_text,
                        font_size=sp(14),
                        color=(0.6, 0.8, 0.6, 1),
                        font_name='simhei'
                    ))
            
            content.add_widget(records_layout)
        
        else:
            # 无记录提示
            no_record_label = Label(
                text='暂无记录',
                font_size=sp(16),
                color=(0.7, 0.7, 0.7, 1),
                italic=True,
                font_name='simhei'
            )
            content.add_widget(no_record_label)
        
        # 按钮区域
        buttons_layout = GridLayout(cols=2, spacing=dp(10), size_hint_y=0.3)
        
        add_buttons = [
            ('📅 记录经期', 'period', (0.93, 0.6, 0.73, 1)),
            ('😊 记录心情', 'mood', (0.8, 0.8, 0.4, 1)),
            ('💖 记录爱爱', 'intimacy', (0.6, 0.8, 0.6, 1)),
            ('✏️ 编辑记录', 'edit', (0.7, 0.7, 0.9, 1)),
        ]
        
        for text, record_type, color in add_buttons:
            btn = Button(
                text=text,
                background_color=color,
                color=(1, 1, 1, 1),
                font_size=sp(13),
                font_name='simhei',
                bold=True
            )
            btn.bind(on_press=lambda instance, d=date, rt=record_type: self.add_record(d, rt))
            buttons_layout.add_widget(btn)
        
        content.add_widget(buttons_layout)
        
        # 关闭按钮
        close_btn = Button(
            text='关闭',
            size_hint_y=None,
            height=dp(40),
            background_color=(0.9, 0.9, 0.9, 1),
            color=(0.4, 0.4, 0.4, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.85, 0.6),
            separator_height=0,
            background=''
        )
        
        with popup.canvas.before:
            Color(0.98, 0.96, 0.97, 1)
            Rectangle(pos=popup.pos, size=popup.size)
        
        close_btn.bind(on_press=popup.dismiss)
        content.add_widget(close_btn)
        
        popup.open()
    
    def add_record(self, date, record_type):
        """添加记录"""
        app = App.get_running_app()
        
        if record_type == 'period':
            # 记录经期
            popup = self.create_period_popup(date)
            popup.open()
        
        elif record_type == 'mood':
            # 记录心情
            popup = self.create_mood_popup(date)
            popup.open()
        
        elif record_type == 'intimacy':
            # 记录爱爱
            popup = self.create_intimacy_popup(date)
            popup.open()
        
        elif record_type == 'edit':
            # 编辑记录（这里简化处理，实际应用中应该更复杂）
            app.show_popup('提示', '编辑功能开发中...')
    
    def create_period_popup(self, date):
        """创建记录经期的弹窗"""
        content = BoxLayout(orientation='vertical', spacing=dp(15), padding=dp(20))
        
        # 标题
        title = Label(
            text=f"记录经期\n{date.strftime('%Y年%m月%d日')}",
            font_size=sp(18),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(title)
        
        # 日期输入
        date_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.3)
        date_layout.add_widget(Label(
            text='开始日期:',
            font_size=sp(14),
            color=(0.4, 0.2, 0.3, 1),
            size_hint_x=0.4,
            font_name='simhei'
        ))
        
        date_input = PrettyTextInput(
            text=date.strftime('%Y-%m-%d'),
            size_hint_x=0.6
        )
        date_layout.add_widget(date_input)
        
        # 时长选择
        duration_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.3)
        duration_layout.add_widget(Label(
            text='持续天数:',
            font_size=sp(14),
            color=(0.4, 0.2, 0.3, 1),
            size_hint_x=0.4,
            font_name='simhei'
        ))
        
        duration_buttons = GridLayout(cols=5, spacing=dp(5), size_hint_x=0.6)
        for days in [3, 4, 5, 6, 7]:
            btn = ToggleButton(
                text=str(days),
                group='duration',
                size_hint=(0.2, 1),
                background_color=(0.95, 0.95, 0.95, 1)
            )
            if days == 5:  # 默认选择5天
                btn.state = 'down'
            duration_buttons.add_widget(btn)
        
        duration_layout.add_widget(duration_buttons)
        content.add_widget(date_layout)
        content.add_widget(duration_layout)
        
        # 按钮
        buttons_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.2)
        
        save_btn = Button(
            text='保存',
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        cancel_btn = Button(
            text='取消',
            background_color=(0.9, 0.9, 0.9, 1),
            color=(0.4, 0.4, 0.4, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.8, 0.5),
            separator_height=0
        )
        
        def save_period(instance):
            try:
                start_date = datetime.strptime(date_input.text, '%Y-%m-%d')
                
                # 获取选择的持续时间
                duration = 5  # 默认
                for child in duration_buttons.children:
                    if child.state == 'down':
                        duration = int(child.text)
                        break
                
                end_date = start_date + timedelta(days=duration - 1)
                
                app = App.get_running_app()
                record = {
                    'start_date': start_date.strftime('%Y-%m-%d'),
                    'end_date': end_date.strftime('%Y-%m-%d'),
                    'type': 'period',
                    'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                }
                
                if app.save_record(record):
                    self.update_calendar()
                    self.update_status()
                    popup.dismiss()
                    app.show_popup('成功', '经期记录已保存！')
            
            except ValueError:
                app.show_popup('错误', '日期格式不正确！')
        
        save_btn.bind(on_press=save_period)
        cancel_btn.bind(on_press=popup.dismiss)
        
        buttons_layout.add_widget(save_btn)
        buttons_layout.add_widget(cancel_btn)
        content.add_widget(buttons_layout)
        
        return popup
    
    def create_mood_popup(self, date):
        """创建记录心情的弹窗"""
        content = BoxLayout(orientation='vertical', spacing=dp(10), padding=dp(15))
        
        title = Label(
            text=f"记录心情\n{date.strftime('%Y年%m月%d日')}",
            font_size=sp(18),
            bold=True,
            color=(0.8, 0.8, 0.4, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(title)
        
        # 心情选择
        moods = ['😊 开心', '😢 难过', '😠 生气', '😌 平静', 
                '😫 疲惫', '😖 压力', '😍 兴奋', '😨 焦虑']
        
        mood_grid = GridLayout(cols=4, spacing=dp(5), size_hint_y=0.5)
        self.selected_mood = None
        
        for mood in moods:
            btn = ToggleButton(
                text=mood,
                group='mood',
                size_hint=(0.25, 0.2),
                background_color=(0.95, 0.95, 0.95, 1),
                font_name='simhei'
            )
            btn.bind(on_press=lambda instance, m=mood: self.select_mood(m))
            mood_grid.add_widget(btn)
        
        content.add_widget(mood_grid)
        
        # 症状选择
        symptoms = ['腹痛', '头痛', '背痛', '乳房胀痛', 
                   '疲劳', '情绪波动', '食欲变化', '其他']
        
        symptom_layout = GridLayout(cols=2, spacing=dp(5), size_hint_y=0.3)
        self.selected_symptoms = []
        
        for symptom in symptoms:
            btn = ToggleButton(
                text=symptom,
                group='symptom',
                size_hint=(0.5, 0.25),
                background_color=(0.95, 0.95, 0.95, 1),
                font_name='simhei'
            )
            btn.bind(on_press=lambda instance, s=symptom: self.toggle_symptom(s, instance))
            symptom_layout.add_widget(btn)
        
        content.add_widget(symptom_layout)
        
        # 按钮
        buttons_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.2)
        
        save_btn = Button(
            text='保存',
            background_color=(0.8, 0.8, 0.4, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        cancel_btn = Button(
            text='取消',
            background_color=(0.9, 0.9, 0.9, 1),
            color=(0.4, 0.4, 0.4, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.85, 0.7),
            separator_height=0
        )
        
        def save_mood(instance):
            if not self.selected_mood:
                app = App.get_running_app()
                app.show_popup('提示', '请选择心情！')
                return
            
            app = App.get_running_app()
            record = {
                'date': date.strftime('%Y-%m-%d'),
                'mood': self.selected_mood,
                'symptoms': self.selected_symptoms.copy(),
                'type': 'mood_symptom',
                'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
            
            if app.save_record(record):
                self.update_calendar()
                popup.dismiss()
                app.show_popup('成功', '心情记录已保存！')
        
        save_btn.bind(on_press=save_mood)
        cancel_btn.bind(on_press=popup.dismiss)
        
        buttons_layout.add_widget(save_btn)
        buttons_layout.add_widget(cancel_btn)
        content.add_widget(buttons_layout)
        
        return popup
    
    def create_intimacy_popup(self, date):
        """创建记录爱爱的弹窗"""
        content = BoxLayout(orientation='vertical', spacing=dp(10), padding=dp(15))
        
        title = Label(
            text=f"记录爱爱\n{date.strftime('%Y年%m月%d日')}",
            font_size=sp(18),
            bold=True,
            color=(0.6, 0.8, 0.6, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(title)
        
        # 类型选择
        types = ['内射', '外射', '戴套', '避孕药', '其他']
        
        type_layout = GridLayout(cols=2, spacing=dp(5), size_hint_y=0.4)
        self.selected_type = None
        
        for intimacy_type in types:
            btn = ToggleButton(
                text=intimacy_type,
                group='intimacy',
                size_hint=(0.5, 0.2),
                background_color=(0.95, 0.95, 0.95, 1),
                font_name='simhei'
            )
            btn.bind(on_press=lambda instance, t=intimacy_type: self.select_intimacy_type(t))
            type_layout.add_widget(btn)
        
        content.add_widget(type_layout)
        
        # 备注输入
        note_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.2)
        note_layout.add_widget(Label(
            text='备注:',
            font_size=sp(14),
            color=(0.4, 0.2, 0.3, 1),
            size_hint_x=0.3,
            font_name='simhei'
        ))
        
        note_input = PrettyTextInput(
            hint_text='可添加备注',
            size_hint_x=0.7
        )
        note_layout.add_widget(note_input)
        content.add_widget(note_layout)
        
        # 安全期提醒
        app = App.get_running_app()
        predictor = CyclePredictor(app.load_records())
        next_period_start, _, ovulation_date, fertile_window = predictor.predict_next_period()
        
        reminder_text = ''
        if ovulation_date:
            if date.date() == ovulation_date.date():
                reminder_text = '⚠️ 今天是排卵期，容易怀孕！'
            elif fertile_window and fertile_window[0] <= date <= fertile_window[1]:
                reminder_text = '⚠️ 现在是易孕期，注意避孕！'
        
        if reminder_text:
            reminder_label = Label(
                text=reminder_text,
                font_size=sp(12),
                color=(0.9, 0.4, 0.4, 1),
                bold=True,
                halign='center',
                font_name='simhei'
            )
            content.add_widget(reminder_label)
        
        # 按钮
        buttons_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.2)
        
        save_btn = Button(
            text='保存',
            background_color=(0.6, 0.8, 0.6, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        cancel_btn = Button(
            text='取消',
            background_color=(0.9, 0.9, 0.9, 1),
            color=(0.4, 0.4, 0.4, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.8, 0.6),
            separator_height=0
        )
        
        def save_intimacy(instance):
            if not self.selected_type:
                app = App.get_running_app()
                app.show_popup('提示', '请选择类型！')
                return
            
            app = App.get_running_app()
            record = {
                'date': date.strftime('%Y-%m-%d'),
                'type': 'intimacy',
                'intimacy_type': self.selected_type,
                'note': note_input.text,
                'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
            
            if app.save_record(record):
                self.update_calendar()
                popup.dismiss()
                app.show_popup('成功', '爱爱记录已保存！')
        
        save_btn.bind(on_press=save_intimacy)
        cancel_btn.bind(on_press=popup.dismiss)
        
        buttons_layout.add_widget(save_btn)
        buttons_layout.add_widget(cancel_btn)
        content.add_widget(buttons_layout)
        
        return popup
    
    def select_mood(self, mood):
        self.selected_mood = mood
    
    def toggle_symptom(self, symptom, instance):
        if instance.state == 'down':
            if symptom not in self.selected_symptoms:
                self.selected_symptoms.append(symptom)
        else:
            if symptom in self.selected_symptoms:
                self.selected_symptoms.remove(symptom)
    
    def select_intimacy_type(self, intimacy_type):
        self.selected_type = intimacy_type
    
    def prev_month(self, instance):
        """上一月"""
        if self.current_date.month == 1:
            self.current_date = datetime(self.current_date.year - 1, 12, 1)
        else:
            self.current_date = datetime(self.current_date.year, self.current_date.month - 1, 1)
        self.update_calendar()
        self.update_status()
    
    def next_month(self, instance):
        """下一月"""
        if self.current_date.month == 12:
            self.current_date = datetime(self.current_date.year + 1, 1, 1)
        else:
            self.current_date = datetime(self.current_date.year, self.current_date.month + 1, 1)
        self.update_calendar()
        self.update_status()
    
    def go_to_today(self, instance):
        """回到今天"""
        self.current_date = datetime.now()
        self.update_calendar()
        self.update_status()
    
    def update_status(self):
        """更新状态显示"""
        app = App.get_running_app()
        records = app.load_records()
        
        if not records:
            self.status_label.text = '欢迎使用经期记录！请点击日期开始记录。'
            return
        
        # 使用智能预测
        predictor = CyclePredictor(records)
        next_period_start, next_period_end, ovulation_date, fertile_window = predictor.predict_next_period()
        
        today = datetime.now()
        
        if next_period_start:
            days_to_next = (next_period_start - today).days
            
            if days_to_next > 0:
                status_text = f"📅 下次经期: {next_period_start.strftime('%m月%d日')} ({days_to_next}天后)"
            elif days_to_next == 0:
                status_text = "📅 经期今天开始"
            else:
                status_text = "📅 经期预测已过，请更新记录"
            
            # 添加排卵期提醒
            if ovulation_date:
                days_to_ovulation = (ovulation_date - today).days
                if -2 <= days_to_ovulation <= 2:
                    status_text += f"\n🥚 排卵期: {'今明两天' if days_to_ovulation in [0,1] else ovulation_date.strftime('%m月%d日')}"
            
            self.status_label.text = status_text
        else:
            self.status_label.text = '记录至少两次经期以获得预测'
    
    def show_add_menu(self, instance):
        """显示添加菜单"""
        content = BoxLayout(orientation='vertical', spacing=dp(10), padding=dp(20))
        
        title = Label(
            text='添加记录',
            font_size=sp(18),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(title)
        
        # 按钮
        buttons = [
            ('📅 记录经期', (0.93, 0.6, 0.73, 1)),
            ('😊 记录心情', (0.8, 0.8, 0.4, 1)),
            ('💖 记录爱爱', (0.6, 0.8, 0.6, 1)),
            ('📝 快速笔记', (0.7, 0.7, 0.9, 1)),
        ]
        
        for text, color in buttons:
            btn = Button(
                text=text,
                background_color=color,
                color=(1, 1, 1, 1),
                font_size=sp(15),
                font_name='simhei',
                size_hint_y=0.2
            )
            
            if text == '📅 记录经期':
                btn.bind(on_press=lambda x: self.add_record(datetime.now(), 'period'))
            elif text == '😊 记录心情':
                btn.bind(on_press=lambda x: self.add_record(datetime.now(), 'mood'))
            elif text == '💖 记录爱爱':
                btn.bind(on_press=lambda x: self.add_record(datetime.now(), 'intimacy'))
            else:
                btn.bind(on_press=lambda x: self.quick_note())
            
            content.add_widget(btn)
        
        close_btn = Button(
            text='关闭',
            size_hint_y=0.15,
            background_color=(0.9, 0.9, 0.9, 1),
            color=(0.4, 0.4, 0.4, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.8, 0.6),
            separator_height=0
        )
        
        close_btn.bind(on_press=popup.dismiss)
        content.add_widget(close_btn)
        
        popup.open()
    
    def quick_note(self):
        """快速笔记"""
        app = App.get_running_app()
        app.show_popup('提示', '快速笔记功能开发中...')
    
    def show_bottom_sheet(self, function):
        """显示底部功能表"""
        if function == 'stats':
            self.show_statistics()
        elif function == 'reminders':
            self.show_reminders()
        elif function == 'charts':
            self.show_charts()
        elif function == 'settings':
            self.manager.current = 'settings'
    
    def show_statistics(self):
        """显示统计信息"""
        app = App.get_running_app()
        records = app.load_records()
        
        if not records:
            app.show_popup('统计', '暂无数据')
            return
        
        predictor = CyclePredictor(records)
        stats = predictor.get_cycle_statistics()
        
        if not stats:
            app.show_popup('统计', '需要至少两次经期记录')
            return
        
        content = BoxLayout(orientation='vertical', spacing=dp(10), padding=dp(20))
        
        title = Label(
            text='📊 周期统计',
            font_size=sp(20),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(title)
        
        # 统计信息
        stats_text = f"""
        平均周期: {stats['avg_cycle']:.1f} 天
        最短周期: {stats['min_cycle']} 天
        最长周期: {stats['max_cycle']} 天
        周期次数: {stats['cycle_count']} 次
        规律性: {100 - stats['irregularity']:.1f}%
        """
        
        stats_label = Label(
            text=stats_text,
            font_size=sp(16),
            color=(0.4, 0.2, 0.3, 1),
            halign='left',
            font_name='simhei'
        )
        content.add_widget(stats_label)
        
        close_btn = Button(
            text='关闭',
            size_hint_y=0.15,
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.85, 0.5),
            separator_height=0
        )
        
        close_btn.bind(on_press=popup.dismiss)
        content.add_widget(close_btn)
        
        popup.open()
    
    def show_reminders(self):
        """显示提醒"""
        app = App.get_running_app()
        records = app.load_records()
        predictor = CyclePredictor(records)
        
        next_period_start, next_period_end, ovulation_date, fertile_window = predictor.predict_next_period()
        
        content = BoxLayout(orientation='vertical', spacing=dp(10), padding=dp(20))
        
        title = Label(
            text='🔔 提醒',
            font_size=sp(20),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(title)
        
        today = datetime.now()
        reminders = []
        
        if next_period_start:
            days_to_next = (next_period_start - today).days
            
            if 0 <= days_to_next <= 3:
                reminders.append(f"📅 经期将在{days_to_next}天后开始")
            
            if next_period_start.date() == today.date():
                reminders.append("📅 今天是经期开始日")
        
        if ovulation_date:
            days_to_ovulation = (ovulation_date - today).days
            
            if 0 <= days_to_ovulation <= 2:
                reminders.append(f"🥚 排卵期将在{days_to_ovulation}天后")
            
            if ovulation_date.date() == today.date():
                reminders.append("🥚 今天是排卵期")
        
        if fertile_window:
            if fertile_window[0] <= today <= fertile_window[1]:
                days_left = (fertile_window[1] - today).days
                reminders.append(f"⚠️ 易孕期，还有{days_left}天结束")
        
        if not reminders:
            reminders.append("暂无近期提醒")
        
        reminders_text = '\n\n'.join(reminders)
        
        reminders_label = Label(
            text=reminders_text,
            font_size=sp(16),
            color=(0.4, 0.2, 0.3, 1),
            halign='center',
            font_name='simhei'
        )
        content.add_widget(reminders_label)
        
        close_btn = Button(
            text='关闭',
            size_hint_y=0.15,
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='',
            content=content,
            size_hint=(0.85, 0.5),
            separator_height=0
        )
        
        close_btn.bind(on_press=popup.dismiss)
        content.add_widget(close_btn)
        
        popup.open()
    
    def show_charts(self):
        """显示图表"""
        app = App.get_running_app()
        self.manager.current = 'charts'

# ============================================
# 图表屏幕
# ============================================

class ChartsScreen(Screen):
    """图表屏幕"""
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = 'charts'
        
        # 主布局
        layout = BoxLayout(orientation='vertical', padding=dp(10), spacing=dp(10))
        
        # 标题栏
        header = BoxLayout(orientation='horizontal', size_hint_y=0.1)
        
        back_btn = Button(
            text='返回',
            size_hint=(0.2, 1),
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        back_btn.bind(on_press=lambda x: setattr(self.manager, 'current', 'main_calendar'))
        
        title = Label(
            text='📈 图表分析',
            font_size=sp(22),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            font_name='simhei'
        )
        
        header.add_widget(back_btn)
        header.add_widget(title)
        
        # 滚动视图
        scroll = ScrollView(size_hint=(1, 0.9))
        content = BoxLayout(orientation='vertical', spacing=dp(20), padding=dp(15),
                          size_hint_y=None)
        content.bind(minimum_height=content.setter('height'))
        
        layout.add_widget(header)
        layout.add_widget(scroll)
        
        # 更新图表内容
        Clock.schedule_once(lambda dt: self.update_charts(content), 0.1)
        
        scroll.add_widget(content)
        self.add_widget(layout)
    
    def update_charts(self, content):
        """更新图表内容"""
        content.clear_widgets()
        
        app = App.get_running_app()
        records = app.load_records()
        
        if not records:
            no_data_label = Label(
                text='暂无数据',
                font_size=sp(18),
                color=(0.7, 0.7, 0.7, 1),
                italic=True,
                font_name='simhei'
            )
            content.add_widget(no_data_label)
            return
        
        predictor = CyclePredictor(records)
        stats = predictor.get_cycle_statistics()
        
        if stats and len(stats.get('cycle_lengths', [])) >= 2:
            # 周期长度折线图
            cycle_chart_title = Label(
                text='周期长度变化趋势',
                font_size=sp(18),
                bold=True,
                color=(0.93, 0.6, 0.73, 1),
                size_hint_y=None,
                height=dp(30),
                font_name='simhei'
            )
            content.add_widget(cycle_chart_title)
            
            chart_container = BoxLayout(size_hint_y=None, height=dp(200))
            chart = CycleChart(stats['cycle_lengths'])
            chart_container.add_widget(chart)
            content.add_widget(chart_container)
        
        # 症状频率统计
        symptom_data = self.analyze_symptoms(records)
        if symptom_data:
            symptom_title = Label(
                text='症状频率分析',
                font_size=sp(18),
                bold=True,
                color=(0.8, 0.8, 0.4, 1),
                size_hint_y=None,
                height=dp(30),
                font_name='simhei'
            )
            content.add_widget(symptom_title)
            
            symptom_container = BoxLayout(size_hint_y=None, height=dp(250))
            chart = SymptomChart(symptom_data)
            symptom_container.add_widget(chart)
            content.add_widget(symptom_container)
        
        # 添加预测信息
        if stats:
            prediction_title = Label(
                text='智能预测',
                font_size=sp(18),
                bold=True,
                color=(0.6, 0.8, 0.6, 1),
                size_hint_y=None,
                height=dp(30),
                font_name='simhei'
            )
            content.add_widget(prediction_title)
            
            next_period_start, next_period_end, ovulation_date, fertile_window = predictor.predict_next_period()
            
            if next_period_start:
                today = datetime.now()
                days_to_next = (next_period_start - today).days
                
                prediction_text = f"""
                下次经期预测: {next_period_start.strftime('%Y年%m月%d日')}
                距离今天: {days_to_next}天
                预测排卵期: {ovulation_date.strftime('%m月%d日') if ovulation_date else '暂无'}
                周期规律性: {100 - stats['irregularity']:.1f}%
                """
                
                prediction_label = Label(
                    text=prediction_text,
                    font_size=sp(15),
                    color=(0.4, 0.2, 0.3, 1),
                    halign='left',
                    size_hint_y=None,
                    height=dp(120),
                    font_name='simhei'
                )
                content.add_widget(prediction_label)
        
        # 设置最小高度
        content.height = len(content.children) * dp(100)
    
    def analyze_symptoms(self, records):
        """分析症状频率"""
        symptom_count = defaultdict(int)
        
        for record in records:
            if record.get('type') == 'mood_symptom':
                symptoms = record.get('symptoms', [])
                for symptom in symptoms:
                    # 清理症状文本
                    clean_symptom = symptom.replace('其他: ', '').strip()
                    if clean_symptom:
                        symptom_count[clean_symptom] += 1
        
        # 只保留频率最高的5个症状
        sorted_symptoms = sorted(symptom_count.items(), key=lambda x: x[1], reverse=True)[:5]
        
        return dict(sorted_symptoms)

# ============================================
# 其他屏幕（设置、历史记录）
# ============================================

class SettingsScreen(Screen):
    """设置屏幕"""
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = 'settings'
        
        layout = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(15))
        
        title = Label(
            text='⚙️ 设置',
            font_size=sp(24),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            size_hint_y=0.1,
            font_name='simhei'
        )
        
        # 设置选项
        settings_layout = BoxLayout(orientation='vertical', size_hint_y=0.7, spacing=dp(10))
        
        # 提醒设置
        reminder_layout = BoxLayout(orientation='horizontal', size_hint_y=0.15, spacing=dp(10))
        reminder_label = PrettyLabel(text='经期提醒:')
        reminder_toggle = ToggleButton(
            text='开启',
            state='down',
            size_hint_x=0.3,
            background_color=(0.95, 0.95, 0.95, 1)
        )
        reminder_layout.add_widget(reminder_label)
        reminder_layout.add_widget(reminder_toggle)
        
        # 数据管理
        data_buttons = GridLayout(cols=2, spacing=dp(10), size_hint_y=0.3)
        
        export_btn = RoundedButton(
            text='导出数据',
            background_color=[0.6, 0.8, 0.6, 1]
        )
        export_btn.bind(on_press=self.export_data)
        
        import_btn = RoundedButton(
            text='导入数据',
            background_color=[0.8, 0.8, 0.6, 1]
        )
        import_btn.bind(on_press=self.import_data)
        
        backup_btn = RoundedButton(
            text='备份数据',
            background_color=[0.6, 0.7, 0.9, 1]
        )
        backup_btn.bind(on_press=self.backup_data)
        
        clear_btn = RoundedButton(
            text='清除数据',
            background_color=[0.8, 0.6, 0.6, 1]
        )
        clear_btn.bind(on_press=self.clear_data)
        
        data_buttons.add_widget(export_btn)
        data_buttons.add_widget(import_btn)
        data_buttons.add_widget(backup_btn)
        data_buttons.add_widget(clear_btn)
        
        # 关于
        about_layout = BoxLayout(orientation='vertical', size_hint_y=0.4, spacing=dp(5))
        about_label = PrettyLabel(text='关于经期记录')
        about_label.font_size = sp(18)
        about_label.color = (0.93, 0.6, 0.73, 1)
        
        about_text = """
        版本: 爱你1.0
        开发者: 廿巳
        """
        
        about_content = Label(
            text=about_text,
            font_size=sp(14),
            color=(0.5, 0.5, 0.5, 1),
            halign='left',
            valign='top',
            font_name='simhei'
        )
        about_content.bind(size=about_content.setter('text_size'))
        
        about_layout.add_widget(about_label)
        about_layout.add_widget(about_content)
        
        # 按钮
        button_layout = BoxLayout(orientation='horizontal', spacing=dp(10), size_hint_y=0.1)
        
        back_btn = RoundedButton(
            text='返回',
            background_color=[0.7, 0.7, 0.7, 1]
        )
        back_btn.bind(on_press=lambda x: setattr(self.manager, 'current', 'main_calendar'))
        
        button_layout.add_widget(back_btn)
        
        settings_layout.add_widget(reminder_layout)
        settings_layout.add_widget(data_buttons)
        settings_layout.add_widget(about_layout)
        
        layout.add_widget(title)
        layout.add_widget(settings_layout)
        layout.add_widget(button_layout)
        
        self.add_widget(layout)
    
    def export_data(self, instance):
        app = App.get_running_app()
        app.show_popup('导出', '数据导出功能开发中...')
    
    def import_data(self, instance):
        app = App.get_running_app()
        app.show_popup('导入', '数据导入功能开发中...')
    
    def backup_data(self, instance):
        app = App.get_running_app()
        app.show_popup('备份', '数据备份功能开发中...')
    
    def clear_data(self, instance):
        content = BoxLayout(orientation='vertical', padding=dp(10), spacing=dp(10))
        content.add_widget(Label(
            text='确定要清除所有记录吗？\n此操作不可恢复！',
            font_size=sp(16),
            color=(0.4, 0.2, 0.3, 1),
            font_name='simhei'
        ))
        
        btn_layout = BoxLayout(orientation='horizontal', spacing=dp(10), 
                              size_hint_y=None, height=dp(50))
        
        confirm_btn = Button(
            text='确定',
            background_color=[0.8, 0.3, 0.3, 1],
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        cancel_btn = Button(
            text='取消',
            background_color=[0.7, 0.7, 0.7, 1],
            color=(0.4, 0.4, 0.4, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title='清除数据',
            content=content,
            size_hint=(0.8, 0.4)
        )
        
        def confirm_clear(instance):
            app = App.get_running_app()
            if app.clear_all_records():
                popup.dismiss()
                app.show_popup('成功', '所有记录已清除')
            else:
                app.show_popup('错误', '清除失败')
        
        confirm_btn.bind(on_press=confirm_clear)
        cancel_btn.bind(on_press=popup.dismiss)
        
        btn_layout.add_widget(confirm_btn)
        btn_layout.add_widget(cancel_btn)
        
        content.add_widget(btn_layout)
        popup.open()

class HistoryScreen(Screen):
    """历史记录屏幕"""
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.name = 'history'
        
        layout = BoxLayout(orientation='vertical', padding=dp(10), spacing=dp(10))
        
        # 标题
        header = BoxLayout(orientation='horizontal', size_hint_y=0.1)
        
        back_btn = Button(
            text='返回',
            size_hint=(0.2, 1),
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        back_btn.bind(on_press=lambda x: setattr(self.manager, 'current', 'main_calendar'))
        
        title = Label(
            text='📊 历史记录',
            font_size=sp(22),
            bold=True,
            color=(0.93, 0.6, 0.73, 1),
            font_name='simhei'
        )
        
        header.add_widget(back_btn)
        header.add_widget(title)
        
        # 筛选按钮
        filter_layout = BoxLayout(orientation='horizontal', size_hint_y=0.08, spacing=dp(5))
        
        filter_buttons = ['全部', '经期', '心情', '爱爱']
        self.active_filter = '全部'
        
        for filter_type in filter_buttons:
            btn = ToggleButton(
                text=filter_type,
                group='filter',
                size_hint=(0.25, 1),
                background_color=(0.95, 0.95, 0.95, 1),
                font_name='simhei'
            )
            if filter_type == '全部':
                btn.state = 'down'
            btn.bind(on_press=lambda instance, ft=filter_type: self.filter_records(ft))
            filter_layout.add_widget(btn)
        
        # 滚动视图
        scroll = ScrollView(size_hint=(1, 0.82))
        self.history_layout = GridLayout(cols=1, spacing=dp(10), size_hint_y=None)
        self.history_layout.bind(minimum_height=self.history_layout.setter('height'))
        
        layout.add_widget(header)
        layout.add_widget(filter_layout)
        layout.add_widget(scroll)
        
        scroll.add_widget(self.history_layout)
        
        self.add_widget(layout)
    
    def on_enter(self):
        self.update_history()
    
    def filter_records(self, filter_type):
        """筛选记录"""
        self.active_filter = filter_type
        self.update_history()
    
    def update_history(self):
        """更新历史记录显示"""
        self.history_layout.clear_widgets()
        
        app = App.get_running_app()
        records = app.load_records()
        
        if not records:
            no_record_label = Label(
                text='暂无记录',
                font_size=sp(18),
                color=(0.7, 0.7, 0.7, 1),
                italic=True,
                font_name='simhei'
            )
            self.history_layout.add_widget(no_record_label)
            return
        
        # 按日期分组
        records_by_date = defaultdict(list)
        for record in records:
            # 根据筛选条件过滤
            if self.active_filter == '全部' or \
               (self.active_filter == '经期' and record.get('type') == 'period') or \
               (self.active_filter == '心情' and record.get('type') == 'mood_symptom') or \
               (self.active_filter == '爱爱' and record.get('type') == 'intimacy'):
                
                date_key = record.get('date') or record.get('start_date')
                if date_key:
                    records_by_date[date_key].append(record)
        
        # 按日期排序（最近的在前）
        sorted_dates = sorted(records_by_date.keys(), reverse=True)
        
        for date_key in sorted_dates:
            # 日期标题
            date_label = Label(
                text=f"📅 {date_key}",
                font_size=sp(18),
                bold=True,
                color=(0.93, 0.6, 0.73, 1),
                size_hint_y=None,
                height=dp(40),
                halign='left',
                font_name='simhei'
            )
            self.history_layout.add_widget(date_label)
            
            # 该日期的所有记录
            for record in records_by_date[date_key]:
                record_type = record.get('type', '')
                
                if record_type == 'period':
                    start = record.get('start_date', '')
                    end = record.get('end_date', '')
                    
                    if start == end:
                        text = f"  经期: {start}"
                        color = (0.93, 0.6, 0.73, 1)
                    else:
                        text = f"  经期: {start} 至 {end}"
                        color = (0.93, 0.6, 0.73, 0.8)
                    
                    icon = '🩸'
                
                elif record_type == 'mood_symptom':
                    mood = record.get('mood', '未知')
                    symptoms = record.get('symptoms', [])
                    symptoms_text = ', '.join(symptoms[:3]) if symptoms else '无'
                    if len(symptoms) > 3:
                        symptoms_text += '...'
                    
                    text = f"  心情: {mood}"
                    if symptoms_text != '无':
                        text += f", 症状: {symptoms_text}"
                    
                    color = (0.8, 0.8, 0.4, 1)
                    icon = '😊'
                
                elif record_type == 'intimacy':
                    intimacy_type = record.get('intimacy_type', '未知')
                    note = record.get('note', '')
                    note_text = f" ({note})" if note else ''
                    text = f"  爱爱: {intimacy_type}{note_text}"
                    color = (0.6, 0.8, 0.6, 1)
                    icon = '💖'
                
                else:
                    continue
                
                record_label = Label(
                    text=f"{icon} {text}",
                    font_size=sp(14),
                    color=color,
                    size_hint_y=None,
                    height=dp(35),
                    halign='left',
                    font_name='simhei'
                )
                self.history_layout.add_widget(record_label)

# ============================================
# 主应用
# ============================================

class PeriodTrackerApp(App):
    """主应用类"""
    
    def build(self):
        self.title = '经期记录'
        
        # 创建屏幕管理器
        self.sm = ScreenManager()
        
        # 添加屏幕
        screens = [
            MainCalendarScreen(),
            ChartsScreen(),
            HistoryScreen(),
            SettingsScreen()
        ]
        
        for screen in screens:
            self.sm.add_widget(screen)
        
        # 加载数据
        self.load_records()
        
        # 设置初始屏幕
        self.sm.current = 'main_calendar'
        
        return self.sm
    
    def get_data_file_path(self):
        """获取数据文件路径"""
        return 'period_tracker_data.json'
    
    def load_records(self):
        """加载所有记录"""
        data_file = self.get_data_file_path()
        records = []
        
        try:
            if os.path.exists(data_file):
                with open(data_file, 'r', encoding='utf-8') as f:
                    records = json.load(f)
        except Exception as e:
            print(f"加载数据时出错: {e}")
        
        return records
    
    def save_record(self, record):
        """保存一条记录"""
        records = self.load_records()
        records.append(record)
        
        try:
            data_file = self.get_data_file_path()
            with open(data_file, 'w', encoding='utf-8') as f:
                json.dump(records, f, ensure_ascii=False, indent=2)
            
            return True
        except Exception as e:
            print(f"保存数据时出错: {e}")
            return False
    
    def clear_all_records(self):
        """清除所有记录"""
        try:
            data_file = self.get_data_file_path()
            with open(data_file, 'w', encoding='utf-8') as f:
                json.dump([], f)
            
            return True
        except Exception as e:
            print(f"清除数据时出错: {e}")
            return False
    
    def get_records_for_date(self, date):
        """获取指定日期的记录"""
        date_str = date.strftime('%Y-%m-%d')
        records = self.load_records()
        date_records = []
        
        for record in records:
            # 检查经期记录
            if record.get('type') == 'period':
                start_date = record.get('start_date')
                end_date = record.get('end_date') or start_date
                if start_date and end_date:
                    try:
                        start = datetime.strptime(start_date, '%Y-%m-%d')
                        end = datetime.strptime(end_date, '%Y-%m-%d')
                        if start <= date <= end:
                            date_records.append(record)
                    except:
                        pass
            # 检查其他记录
            elif record.get('date') == date_str:
                date_records.append(record)
        
        return date_records
    
    def show_popup(self, title, message):
        """显示弹窗"""
        content = BoxLayout(orientation='vertical', padding=dp(10), spacing=dp(10))
        content.add_widget(Label(
            text=message,
            font_size=sp(16),
            color=(0.4, 0.2, 0.3, 1),
            font_name='simhei'
        ))
        
        btn = Button(
            text='确定',
            size_hint_y=None,
            height=dp(40),
            background_color=(0.93, 0.6, 0.73, 1),
            color=(1, 1, 1, 1),
            font_name='simhei'
        )
        
        popup = Popup(
            title=title,
            content=content,
            size_hint=(0.8, 0.4)
        )
        
        btn.bind(on_press=popup.dismiss)
        content.add_widget(btn)
        popup.open()

# 运行应用
if __name__ == '__main__':
    PeriodTrackerApp().run()