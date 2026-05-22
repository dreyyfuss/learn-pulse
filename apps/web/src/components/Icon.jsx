import {
  AlertCircle, ArrowRight, ArrowLeft, Award, BarChart2, BarChart3, Bell, BookOpen,
  Check, CheckCircle, ChevronDown, ChevronLeft, ChevronRight, ChevronUp,
  ChevronsUpDown, CircleAlert, Compass, Download, Edit3, FileText, Flame, GripVertical,
  HelpCircle, LayoutDashboard, Lock, LogOut, Menu, Paperclip, Pause, Pencil,
  Play, PlayCircle, Plus, Search, Send, Settings, Share2, Sparkles,
  Trash2, User, UserPlus, Users, X, BarChart,
} from 'lucide-react';

const MAP = {
  'alert-circle': AlertCircle, 'circle-alert': CircleAlert,
  'arrow-right': ArrowRight, 'arrow-left': ArrowLeft, 'award': Award,
  'bar-chart-2': BarChart2, 'bar-chart-3': BarChart3, 'bar-chart': BarChart,
  'bell': Bell, 'book-open': BookOpen, 'check': Check, 'check-circle': CheckCircle,
  'chevron-down': ChevronDown, 'chevron-left': ChevronLeft, 'chevron-right': ChevronRight,
  'chevron-up': ChevronUp, 'chevrons-up-down': ChevronsUpDown,
  'compass': Compass, 'download': Download, 'edit-3': Edit3, 'file-text': FileText,
  'flame': Flame, 'grip-vertical': GripVertical, 'help-circle': HelpCircle,
  'layout-dashboard': LayoutDashboard, 'lock': Lock, 'log-out': LogOut,
  'menu': Menu, 'paperclip': Paperclip, 'pause': Pause, 'pencil': Pencil,
  'play': Play, 'play-circle': PlayCircle, 'plus': Plus,
  'search': Search, 'send': Send, 'settings': Settings, 'share-2': Share2,
  'sparkles': Sparkles, 'trash-2': Trash2, 'user': User,
  'user-plus': UserPlus, 'users': Users, 'x': X,
};

export default function Icon({ name, size = 18, color, style, className }) {
  const Comp = MAP[name];
  if (!Comp) return null;
  return (
    <Comp
      size={size}
      color={color}
      style={{ flexShrink: 0, ...style }}
      className={className}
      strokeWidth={1.5}
    />
  );
}
