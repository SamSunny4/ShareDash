using System.Collections;
using System.Collections.Specialized;
using System.Windows;
using System.Windows.Media;
using ShareDash.Windows.Models;

namespace ShareDash.Windows.Controls;

public class ChunkGridControl : FrameworkElement
{
    public static readonly DependencyProperty ChunkStatesProperty =
        DependencyProperty.Register(
            nameof(ChunkStates),
            typeof(IList),
            typeof(ChunkGridControl),
            new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender, OnChunkStatesChanged));

    public static readonly DependencyProperty UsbBrushProperty =
        DependencyProperty.Register(
            nameof(UsbBrush),
            typeof(Brush),
            typeof(ChunkGridControl),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromRgb(0, 210, 255)), FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty WifiBrushProperty =
        DependencyProperty.Register(
            nameof(WifiBrush),
            typeof(Brush),
            typeof(ChunkGridControl),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromRgb(0, 255, 163)), FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty InFlightBrushProperty =
        DependencyProperty.Register(
            nameof(InFlightBrush),
            typeof(Brush),
            typeof(ChunkGridControl),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromRgb(251, 191, 36)), FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty PendingBrushProperty =
        DependencyProperty.Register(
            nameof(PendingBrush),
            typeof(Brush),
            typeof(ChunkGridControl),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromArgb(200, 26, 36, 56)), FrameworkPropertyMetadataOptions.AffectsRender));

    public IList? ChunkStates
    {
        get => (IList?)GetValue(ChunkStatesProperty);
        set => SetValue(ChunkStatesProperty, value);
    }

    public Brush UsbBrush
    {
        get => (Brush)GetValue(UsbBrushProperty);
        set => SetValue(UsbBrushProperty, value);
    }

    public Brush WifiBrush
    {
        get => (Brush)GetValue(WifiBrushProperty);
        set => SetValue(WifiBrushProperty, value);
    }

    public Brush InFlightBrush
    {
        get => (Brush)GetValue(InFlightBrushProperty);
        set => SetValue(InFlightBrushProperty, value);
    }

    public Brush PendingBrush
    {
        get => (Brush)GetValue(PendingBrushProperty);
        set => SetValue(PendingBrushProperty, value);
    }

    private static void OnChunkStatesChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is ChunkGridControl control)
        {
            if (e.OldValue is INotifyCollectionChanged oldColl)
            {
                oldColl.CollectionChanged -= control.OnCollectionChanged;
            }
            if (e.NewValue is INotifyCollectionChanged newColl)
            {
                newColl.CollectionChanged += control.OnCollectionChanged;
            }
            control.InvalidateVisual();
        }
    }

    private void OnCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        InvalidateVisual();
    }

    public void Refresh()
    {
        InvalidateVisual();
    }

    protected override void OnRender(DrawingContext dc)
    {
        base.OnRender(dc);

        var list = ChunkStates;
        int total = list?.Count ?? 0;
        if (total == 0) return;

        double w = ActualWidth;
        double h = ActualHeight;
        if (w <= 10 || h <= 10) return;

        int cols = Math.Max((int)Math.Ceiling(Math.Sqrt(total * 2.2)), 16);
        cols = Math.Min(cols, total);
        int rows = Math.Max(1, (int)Math.Ceiling((double)total / cols));

        double gap = 2.5;
        double availableW = Math.Max(1.0, w - (cols - 1) * gap);
        double cellW = Math.Max(1.0, availableW / cols);
        double availableH = Math.Max(1.0, h - (rows - 1) * gap);
        double cellH = Math.Max(1.0, Math.Min(availableH / rows, cellW * 1.3));

        for (int i = 0; i < total; i++)
        {
            var item = list![i] as ChunkVisualItem;
            if (item == null) continue;

            int col = i % cols;
            int row = i / cols;
            double x = col * (cellW + gap);
            double y = row * (cellH + gap);

            Brush brush = item.State switch
            {
                ChunkVisualState.CompletedUsb => UsbBrush,
                ChunkVisualState.CompletedWifi => WifiBrush,
                ChunkVisualState.InFlight => InFlightBrush,
                _ => PendingBrush
            };

            dc.DrawRoundedRectangle(brush, null, new Rect(x, y, cellW, cellH), 2.0, 2.0);
        }
    }
}
