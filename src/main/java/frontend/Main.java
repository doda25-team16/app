package frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import com.doda2025team16.libversion.VersionUtil;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @PostConstruct
    public void printLibraryVersion() {
        System.out.println("================================");
        System.out.println(" SMS-APP STARTED SUCCESSFULLY ");
        System.out.println(" Using libversion: " + VersionUtil.getVersion());
        System.out.println("================================");
    }

}