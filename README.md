# Reactive Spring - Josh Long & Mark Heckler @ Spring I-O 2017

此项目是视频教程[《Reactive Spring - Josh Long & Mark Heckler @ Spring I-O 2017》](https://www.youtube.com/watch?v=tiqqRwHwt98)的练习代码

参考资料
- http://www.reactive-streams.org
- https://projectreactor.io
- https://github.com/joshlong/flux-flix-service

## Foure types:

Publisher, Subscribe, 。。。

两种返回类型：
* Mono (0,1)
* Flex (0,n)

## 新建项目
从 http://start.spring.io 新建 Spring Boot 2.0.0 M7 项目 `flux-movie-service`，使用依赖：

`Lombok, Reactive Web, Reactive MongoDB`

本地运行 MongoDB 3.6
`> mongod.exe`


## 开发 Service

创建 Movie 和 MovieEvent 模型
```java
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
```

Repository
```java
// ReactiveMongoRepository
interface MovieRepository extends ReactiveMongoRepository<Movie, String> {

}
```

CLR
```java
@Component
class SampleMoviesCLR implements CommandLineRunner {
    private final MovieRepository movieRepository;

    SampleMoviesCLR(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public void run(String... strings) throws Exception {
        movieRepository.deleteAll().subscribe(null, null, () -> {
            Stream.of("Movie A", "Movie B")
                    .forEach(title -> movieRepository.save(new Movie(UUID.randomUUID().toString(), title)).subscribe());
            movieRepository.findAll().subscribe(System.out::println);
        });
    }
}

```

## Service
```java
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

    // use flatMap
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
```

RouterFunction
```java
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

    /*    @Bean // 原写法
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
```

## 运行测试
访问
- http://localhost:8080/movies
- http://localhost:8080/movies/某个id
- http://localhost:8080/movies/某个id/events

或者用 curl
