using System;
using System.Diagnostics;
using System.IO;
using System.Text;

internal static class MiniCLauncher
{
    private static int Main(string[] args)
    {
        string root = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string exeName = Path.GetFileNameWithoutExtension(Process.GetCurrentProcess().MainModule.FileName);
        bool isWorkbench = exeName.IndexOf("Workbench", StringComparison.OrdinalIgnoreCase) >= 0 || args.Length == 0;
        string java = Path.Combine(root, "runtime", "java", "bin", "java.exe");
        string classpath = Path.Combine(root, "app", "MiniC", "lib", "*");
        string javafxCache = Path.Combine(root, "cache", "javafx");
        string ml64 = Path.Combine(root, "toolchain", "msvc", "bin", "Hostx64", "x64", "ml64.exe");
        string link = Path.Combine(root, "toolchain", "msvc", "bin", "Hostx64", "x64", "link.exe");
        string msvcRoot = Path.Combine(root, "toolchain", "msvc");
        string msvcBin = Path.Combine(msvcRoot, "bin", "Hostx64", "x64");
        string windowsKitsRoot = Path.Combine(root, "toolchain", "windows-kits");
        string libPaths = string.Join(Path.PathSeparator.ToString(), new[]
        {
            Path.Combine(msvcRoot, "lib", "x64"),
            Path.Combine(windowsKitsRoot, "Lib", "10.0.26100.0", "um", "x64"),
            Path.Combine(windowsKitsRoot, "Lib", "10.0.26100.0", "ucrt", "x64")
        });

        bool hasMl64 = HasOption(args, "--ml64");
        bool hasLink = HasOption(args, "--link");

        ProcessStartInfo start = new ProcessStartInfo(java)
        {
            UseShellExecute = false,
            WorkingDirectory = root,
            Arguments = BuildJavaArguments(classpath, isWorkbench ? "minic.ui.MiniCWorkbenchLauncher" : "minic.Main", args, !isWorkbench && (!hasMl64 || !hasLink), ml64, link, javafxCache)
        };
        start.Environment["MINIC_MSVC_LIB_PATHS"] = libPaths;
        start.Environment["MINIC_MSVC_TOOLCHAIN_ROOT"] = msvcRoot;
        start.Environment["MINIC_WINDOWS_KITS_ROOT"] = windowsKitsRoot;
        start.Environment["PATH"] = msvcBin + Path.PathSeparator + start.Environment["PATH"];

        Process process = Process.Start(start);
        if (process == null)
        {
            throw new InvalidOperationException("Failed to launch MiniC");
        }
        process.WaitForExit();
        return process.ExitCode;
    }

    private static bool HasOption(string[] args, string option)
    {
        foreach (string arg in args)
        {
            if (string.Equals(arg, option, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
        return false;
    }

    private static string BuildJavaArguments(string classpath, string mainClass, string[] args, bool appendToolchain, string ml64, string link, string javafxCache)
    {
        StringBuilder builder = new StringBuilder();
        AppendArg(builder, "-Dfile.encoding=UTF-8");
        AppendArg(builder, "-Djavafx.cachedir=" + javafxCache);
        AppendArg(builder, "-cp");
        AppendArg(builder, classpath);
        AppendArg(builder, mainClass);
        foreach (string arg in args)
        {
            AppendArg(builder, arg);
        }
        if (appendToolchain)
        {
            AppendArg(builder, "--ml64");
            AppendArg(builder, ml64);
            AppendArg(builder, "--link");
            AppendArg(builder, link);
        }
        return builder.ToString();
    }

    private static void AppendArg(StringBuilder builder, string value)
    {
        if (builder.Length > 0)
        {
            builder.Append(' ');
        }
        builder.Append('"');
        int backslashes = 0;
        foreach (char ch in value)
        {
            if (ch == '\\')
            {
                backslashes++;
                continue;
            }
            if (ch == '"')
            {
                builder.Append('\\', backslashes * 2 + 1);
                builder.Append('"');
                backslashes = 0;
                continue;
            }
            builder.Append('\\', backslashes);
            backslashes = 0;
            builder.Append(ch);
        }
        builder.Append('\\', backslashes * 2);
        builder.Append('"');
    }
}
