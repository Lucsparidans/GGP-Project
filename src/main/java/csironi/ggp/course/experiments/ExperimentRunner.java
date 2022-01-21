package csironi.ggp.course.experiments;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import csironi.ggp.course.experiments.tournaments.independentprocesses.IndependentTourneyRunner;

public class ExperimentRunner {

	public static void main(String[] args){
		runExperimentBatches();
	}
	public static void runExperimentBatches(){
        try{
            Path dir = Paths.get("C:/Users/lucsp/Desktop/SplitSetupPC");
            Files.walk(dir).forEach(path -> {

                File file = path.toFile();
                if(file.isFile()){
                    if(file.getName().equals("test.properties")){
                        System.out.println(file.getAbsolutePath());
                        IndependentTourneyRunner.main(new String[]{file.getAbsolutePath()});
                    }
                }
            });
        }catch(Exception e){e.printStackTrace();}
    }

}
