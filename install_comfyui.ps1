$ErrorActionPreference = 'Stop'
$Target   = 'E:\ConfyUI'
$Repo     = 'https://github.com/comfyanonymous/ComfyUI.git'
$Python   = 'D:\python\python.exe'
$VenvPy   = Join-Path $Target 'venv\Scripts\python.exe'
$Proxy    = 'http://127.0.0.1:7890'

# route pip through the local proxy
$env:HTTP_PROXY  = $Proxy
$env:HTTPS_PROXY = $Proxy
$env:ALL_PROXY   = $Proxy

Write-Output "==== STEP 1/5: git clone ComfyUI (via proxy) ===="
if (Test-Path (Join-Path $Target '.git')) {
    Write-Output "Repository already present, skipping clone."
} else {
    if (Test-Path $Target) {
        throw "Target directory $Target exists but is not a git repo; aborting to avoid overwriting."
    }
    git -c http.proxy=$Proxy -c https.proxy=$Proxy clone $Repo $Target
    if ($LASTEXITCODE -ne 0) { throw "git clone failed with exit $LASTEXITCODE" }
}
Write-Output "OK: repository at $Target"

Write-Output "==== STEP 2/5: create virtualenv ===="
if (-not (Test-Path $VenvPy)) {
    & $Python -m venv (Join-Path $Target 'venv')
    if ($LASTEXITCODE -ne 0) { throw "venv creation failed" }
}
Write-Output "OK: venv python at $VenvPy"

Write-Output "==== STEP 3/5: upgrade pip ===="
& $VenvPy -m pip install --upgrade pip setuptools wheel
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed" }

Write-Output "==== STEP 4/5: install CUDA PyTorch (cu124) ===="
& $VenvPy -m pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124
if ($LASTEXITCODE -ne 0) { throw "torch install failed" }

Write-Output "==== STEP 5/5: install ComfyUI requirements ===="
& $VenvPy -m pip install -r (Join-Path $Target 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw "requirements install failed" }

Write-Output "==== ALL INSTALL STEPS COMPLETED ===="
