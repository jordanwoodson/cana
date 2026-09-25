package io.github.samolego.canta.ops;
import io.github.samolego.canta.ops.ShellResult;

interface IShellService {
    void destroy() = 16777114;
    ShellResult exec(in String[] argv, long timeoutMs) = 1;
}
