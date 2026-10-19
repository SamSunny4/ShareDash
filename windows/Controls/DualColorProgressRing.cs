using System.Windows;
using System.Windows.Media;

namespace ShareDash.Windows.Controls;

public class DualColorProgressRing : FrameworkElement
{
    public static readonly DependencyProperty UsbProgressProperty =
        DependencyProperty.Register(
            nameof(UsbProgress),
            typeof(double),
            typeof(DualColorProgressRing),
            new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty WifiProgressProperty =
        DependencyProperty.Register(
            nameof(WifiProgress),
            typeof(double),
            typeof(DualColorProgressRing),
            new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty StrokeThicknessProperty =
        DependencyProperty.Register(
            nameof(StrokeThickness),
            typeof(double),
            typeof(DualColorProgressRing),
            new FrameworkPropertyMetadata(14.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackBrushProperty =
        DependencyProperty.Register(
            nameof(TrackBrush),
            typeof(Brush),
            typeof(DualColorProgressRing),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromArgb(160, 20, 32, 52)), FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty UsbBrushProperty =
        DependencyProperty.Register(
            nameof(UsbBrush),
            typeof(Brush),
            typeof(DualColorProgressRing),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromRgb(0, 210, 255)), FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty WifiBrushProperty =
        DependencyProperty.Register(
            nameof(WifiBrush),
            typeof(Brush),
            typeof(DualColorProgressRing),
            new FrameworkPropertyMetadata(new SolidColorBrush(Color.FromRgb(0, 255, 163)), FrameworkPropertyMetadataOptions.AffectsRender));

    public double UsbProgress
    {
        get => (double)GetValue(UsbProgressProperty);
        set => SetValue(UsbProgressProperty, value);
    }

    public double WifiProgress
    {
        get => (double)GetValue(WifiProgressProperty);
        set => SetValue(WifiProgressProperty, value);
    }

    public double StrokeThickness
    {
        get => (double)GetValue(StrokeThicknessProperty);
        set => SetValue(StrokeThicknessProperty, value);
    }

    public Brush TrackBrush
    {
        get => (Brush)GetValue(TrackBrushProperty);
        set => SetValue(TrackBrushProperty, value);
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

    static DualColorProgressRing()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(DualColorProgressRing), new FrameworkPropertyMetadata(typeof(DualColorProgressRing)));
    }

    protected override void OnRender(DrawingContext dc)
    {
        base.OnRender(dc);

        double w = ActualWidth;
        double h = ActualHeight;
        if (w <= 0 || h <= 0) return;

        double stroke = Math.Max(2.0, StrokeThickness);
        double radius = (Math.Min(w, h) - stroke) / 2.0;
        if (radius <= 1.0) return;

        Point center = new Point(w / 2.0, h / 2.0);

        // 1. Draw background track
        if (TrackBrush != null)
        {
            var trackPen = new Pen(TrackBrush, stroke)
            {
                StartLineCap = PenLineCap.Round,
                EndLineCap = PenLineCap.Round
            };
            trackPen.Freeze();
            dc.DrawEllipse(null, trackPen, center, radius, radius);
        }

        double usbP = Math.Clamp(UsbProgress, 0.0, 1.0);
        double wifiP = Math.Clamp(WifiProgress, 0.0, 1.0);

        double usbSweep = usbP * 360.0;
        double wifiSweep = wifiP * 360.0;

        // Ensure total sweep doesn't exceed 360
        if (usbSweep + wifiSweep > 360.0)
        {
            wifiSweep = Math.Max(0.0, 360.0 - usbSweep);
        }

        // 2. Draw USB Arc (Cyan)
        if (usbSweep > 0.5 && UsbBrush != null)
        {
            var usbPen = new Pen(UsbBrush, stroke)
            {
                StartLineCap = PenLineCap.Round,
                EndLineCap = PenLineCap.Round
            };
            usbPen.Freeze();

            var geom = CreateArcGeometry(center, radius, -90.0, usbSweep);
            dc.DrawGeometry(null, usbPen, geom);
        }

        // 3. Draw Wi-Fi Arc (Green, beginning where USB ends)
        if (wifiSweep > 0.5 && WifiBrush != null)
        {
            var wifiPen = new Pen(WifiBrush, stroke)
            {
                StartLineCap = PenLineCap.Round,
                EndLineCap = PenLineCap.Round
            };
            wifiPen.Freeze();

            double startAngle = -90.0 + usbSweep;
            var geom = CreateArcGeometry(center, radius, startAngle, wifiSweep);
            dc.DrawGeometry(null, wifiPen, geom);
        }
    }

    private static PathGeometry CreateArcGeometry(Point center, double radius, double startAngleDeg, double sweepAngleDeg)
    {
        var geom = new PathGeometry();
        if (sweepAngleDeg <= 0.001) return geom;
        if (sweepAngleDeg >= 360.0) sweepAngleDeg = 359.999;

        double startRad = startAngleDeg * Math.PI / 180.0;
        double endRad = (startAngleDeg + sweepAngleDeg) * Math.PI / 180.0;

        Point pStart = new Point(center.X + radius * Math.Cos(startRad), center.Y + radius * Math.Sin(startRad));
        Point pEnd = new Point(center.X + radius * Math.Cos(endRad), center.Y + radius * Math.Sin(endRad));

        bool isLargeArc = sweepAngleDeg > 180.0;

        var figure = new PathFigure
        {
            StartPoint = pStart,
            IsClosed = false,
            IsFilled = false
        };
        figure.Segments.Add(new ArcSegment(pEnd, new Size(radius, radius), 0, isLargeArc, SweepDirection.Clockwise, true));
        geom.Figures.Add(figure);
        geom.Freeze();
        return geom;
    }
}
