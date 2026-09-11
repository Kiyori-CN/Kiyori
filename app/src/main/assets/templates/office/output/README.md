# output/

产物默认写入本目录，再由 `output_env=android`（默认）回搬到 AI 产物保存位置下的
`office/<task_id>/` 交付；显式 `output_env=linux` 时交付到 Ubuntu 根下的同构目录。
中间文件请留在 Linux 暂存区，不要混入本目录。
