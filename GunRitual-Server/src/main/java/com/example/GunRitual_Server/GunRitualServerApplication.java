	package com.example.GunRitual_Server;

	import org.springframework.boot.SpringApplication;
	import org.springframework.boot.autoconfigure.SpringBootApplication;
	import org.springframework.scheduling.annotation.EnableScheduling;

	@EnableScheduling
	@SpringBootApplication
	public class GunRitualServerApplication {

		public static void main(String[] args) {
			SpringApplication.run(GunRitualServerApplication.class, args);
		}
	}
