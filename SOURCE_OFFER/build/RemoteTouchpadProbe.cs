// SPDX-License-Identifier: Apache-2.0
using System;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;

internal sealed class RemoteTouchpadProbe : Form
{
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr window);

    private readonly string directory;
    private readonly DateTime deadline = DateTime.UtcNow.AddSeconds(100);
    private bool pressed;
    private bool moved;
    private Point start;
    private int down, up, click, drag, vertical, horizontal;
    private bool Foreground { get { return GetForegroundWindow() == Handle; } }

    private RemoteTouchpadProbe(string directory)
    {
        this.directory = directory;
        Text = "Really Decent Portal input test";
        FormBorderStyle = FormBorderStyle.None;
        WindowState = FormWindowState.Maximized;
        BackColor = Color.FromArgb(18, 63, 74);
        TopMost = true;
        KeyPreview = true;
    }

    [STAThread]
    private static void Main(string[] args)
    {
        if (args.Length != 1 || !SystemInformation.TerminalServerSession) return;
        using (var form = new RemoteTouchpadProbe(args[0]))
        using (var timer = new Timer { Interval = 150 }) {
            timer.Tick += (sender, e) => form.Record();
            timer.Start();
            Application.Run(form);
        }
    }

    protected override void OnShown(EventArgs e)
    {
        base.OnShown(e);
        Activate();
        SetForegroundWindow(Handle);
        Cursor.Position = PointToScreen(new Point(Width / 2, Height / 2));
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (e.KeyCode == Keys.Escape) Close();
        base.OnKeyDown(e);
    }

    protected override void OnMouseDown(MouseEventArgs e)
    {
        if (e.Button == MouseButtons.Left) {
            down++;
            pressed = true;
            moved = false;
            start = e.Location;
        }
        base.OnMouseDown(e);
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (pressed && e.Button == MouseButtons.Left &&
            Math.Abs(e.X - start.X) + Math.Abs(e.Y - start.Y) >= 8) {
            moved = true;
        }
        base.OnMouseMove(e);
    }

    protected override void OnMouseUp(MouseEventArgs e)
    {
        if (pressed && e.Button == MouseButtons.Left) {
            up++;
            if (moved) drag++;
            else click++;
            pressed = false;
        }
        base.OnMouseUp(e);
    }

    protected override void OnMouseWheel(MouseEventArgs e)
    {
        vertical += Math.Abs(e.Delta);
        base.OnMouseWheel(e);
    }

    protected override void WndProc(ref Message message)
    {
        const int WM_MOUSEHWHEEL = 0x020E;
        if (message.Msg == WM_MOUSEHWHEEL) {
            horizontal += Math.Abs((int)(short)(message.WParam.ToInt64() >> 16));
        }
        base.WndProc(ref message);
    }

    private void Record()
    {
        if (File.Exists(Path.Combine(directory, "stop")) || DateTime.UtcNow >= deadline) {
            Close();
            return;
        }
        if (!Foreground && DateTime.UtcNow > deadline.AddSeconds(-90)) {
            SetForegroundWindow(Handle);
        }
        string path = Path.Combine(directory, "events.json");
        string next = Path.Combine(directory, "events.next");
        File.WriteAllText(next, string.Format(
            "{{\"run\":\"{0}\",\"foreground\":{1},\"left_down\":{2},\"left_up\":{3},\"click\":{4},\"drag_released\":{5},\"vertical\":{6},\"horizontal\":{7},\"window_width\":{8},\"window_height\":{9}}}",
            Path.GetFileName(directory), Foreground ? "true" : "false", down, up, click, drag, vertical, horizontal, Width, Height));
        try {
            if (File.Exists(path)) File.Replace(next, path, null);
            else File.Move(next, path);
        } catch (IOException) {
            // The controller may be reading the previous frame. Retry next tick.
        }
    }
}
