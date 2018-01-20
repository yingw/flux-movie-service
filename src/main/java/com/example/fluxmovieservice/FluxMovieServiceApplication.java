package com.example.fluxmovieservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.*;

@SpringBootApplication
public class FluxMovieServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxMovieServiceApplication.class, args);
    }
}

@Component
class SampleMoviesCLR implements CommandLineRunner {
    private final MovieRepository movieRepository;

    SampleMoviesCLR(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public void run(String... strings) throws Exception {
        movieRepository.deleteAll().subscribe(null, null, () -> {
            Stream.of("A", "B")
                    .forEach(title -> movieRepository.save(new Movie(UUID.randomUUID().toString(), title)).subscribe());
            movieRepository.findAll().subscribe(System.out::println);
        });
    }
}
/*

@Component
class DataAppInitializr {
    private final MovieRepository movieRepository;

    DataAppInitializr(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run(ApplicationReadyEvent event) {
        movieRepository
                .deleteAll()
                .thenMany(
                        Flux.just("A", "B").flatMap(
                                name -> movieRepository.save(new Movie(name))
                        )
                ).subscribe(null, null, () -> movieRepository.findAll().subscribe(System.out::println));
    }
}
*/

@Service
class FluxMovieService {
    private final MovieRepository movieRepository;

    FluxMovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    Mono<Movie> findById(String id) {
        return movieRepository.findById(id);
    }

    Flux<Movie> findAllMovies() {
        return movieRepository.findAll();
    }

    Flux<MovieEvent> getEvents(String id) {
        return findById(id).flatMapMany(movie -> {
            Flux<MovieEvent> eventFlux = Flux.fromStream(
                    Stream.generate(() -> new MovieEvent(movie, new Date()))
            );
            return eventFlux.zipWith(Flux.interval(Duration.ofSeconds(1)))
                    .map(Tuple2::getT1);
        });
    }
}

@Configuration
class FunctionalReactiveRouterConfiguration {

    @Component
    public static class RouteHandler {
        private final FluxMovieService service;

        public RouteHandler(FluxMovieService service) {
            this.service = service;
        }

        Mono<ServerResponse> all(ServerRequest serverRequest) {
            return ServerResponse.ok().body(service.findAllMovies(), Movie.class);
        }

        Mono<ServerResponse> byId(ServerRequest serverRequest) {
            return ServerResponse.ok().body(service.findById(serverRequest.pathVariable("movieId")), Movie.class);
        }

        Mono<ServerResponse> streams(ServerRequest serverRequest) {
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(service.getEvents(serverRequest.pathVariable("movieId")), MovieEvent.class);
        }
    }

    /*    @Bean
        RouterFunction<?> routes(FluxMovieService service) {
            return RouterFunctions.route(RequestPredicates.GET("/movies"), new HandlerFunction<ServerResponse>() {
                @Override
                public Mono<ServerResponse> handle(ServerRequest serverRequest) {
                    return null;
                }
            });
        }*/
    @Bean
    RouterFunction<?> routes(RouteHandler handler) {
        return route(GET("/movies"), handler::all)
                .andRoute(GET("/movies/{movieId}"), handler::byId)
                .andRoute(GET("/movies/{movieId}/events"), handler::streams);
    }
}


// ReactiveMongoRepository
interface MovieRepository extends ReactiveMongoRepository<Movie, String> {

}

@Document // mongodb
@Data
@NoArgsConstructor
@AllArgsConstructor
class Movie {
    @Id
    private String id; // mongodb's PK using String.
    @NonNull
    private String title;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class MovieEvent {
    private Movie movie;
    private Date when;
}