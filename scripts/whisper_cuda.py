"""Shared NVIDIA CUDA DLL discovery/preload for Faster-Whisper scripts."""

import os


def setup_cuda_dlls():
    import site
    import ctypes

    paths = site.getsitepackages() + [site.getusersitepackages()]
    found = False
    for base in paths:
        cublas_bin = os.path.join(base, "nvidia", "cublas", "bin")
        cudnn_bin = os.path.join(base, "nvidia", "cudnn", "bin")
        for b in [cublas_bin, cudnn_bin]:
            if os.path.exists(b):
                found = True
                try:
                    os.add_dll_directory(b)
                except Exception:
                    pass
                os.environ["PATH"] = b + os.pathsep + os.environ.get("PATH", "")
                for dll in ["cublasLt64_12.dll", "cublas64_12.dll", "cudnn64_9.dll"]:
                    p = os.path.join(b, dll)
                    if os.path.exists(p):
                        try:
                            ctypes.CDLL(p)
                        except Exception:
                            pass
    return found


def cuda_runtime_available() -> bool:
    """Best-effort check that CUDA Whisper runtime DLLs are discoverable."""
    import site

    paths = site.getsitepackages() + [site.getusersitepackages()]
    for base in paths:
        cublas = os.path.join(base, "nvidia", "cublas", "bin", "cublas64_12.dll")
        if os.path.exists(cublas):
            return True
    return False
