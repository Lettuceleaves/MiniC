$ErrorActionPreference = 'Stop'
$VenvPy = 'E:\ConfyUI\venv\Scripts\python.exe'
$Proxy  = 'http://127.0.0.1:7890'

$env:HTTP_PROXY  = $Proxy
$env:HTTPS_PROXY = $Proxy
$env:ALL_PROXY   = $Proxy

Write-Output "==== upgrading PyTorch to cu130 ===="
& $VenvPy -m pip install --upgrade torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu130
if ($LASTEXITCODE -ne 0) { throw "torch cu130 install failed with exit $LASTEXITCODE" }

Write-Output "==== verifying torch/CUDA ===="
& $VenvPy -c "import torch; print('torch', torch.__version__); print('cuda_available', torch.cuda.is_available()); print('device', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"

Write-Output "==== DONE ===="
