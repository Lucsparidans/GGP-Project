#!/usr/local_rwth/bin/zsh

#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --mem-per-cpu=8G
#SBATCH --account="um_dke"
#SBATCH --gres=gpu:1
#SBATCH --time=1-0:00:00
#SBATCH --job-name=GGP
#SBATCH --output=FastResults-%J.log

echo "------------------------------------------------------------"
echo "SLURM JOB ID: $SLURM_JOBID"
echo "Running on nodes: $SLURM_NODELIST"
echo "------------------------------------------------------------"

echo "Starting Experiment"
java -Xmx4096M -jar IndependentTourneyRunner.jar test.properties
echo "DONE RUNNING EXPERIMENT"