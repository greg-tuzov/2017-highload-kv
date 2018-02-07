# Нагрузочное тестирование

## Железо, окружение, инструменты

Тестирование проводилось на машине с 4х-ядерным процессором (Intel Core __i3-6100U CPU__ @ 2.30GHz x 4), __6 ГБ__ ОЗУ, диск - __SSD__ SATA. 

ОС - __Ubuntu 17.10__. JRE - 1.8.0_161.

Нагрузка подавалась при помощи инструмента __yandex-tank__, запущенного внутри docker-контейнера, при этом 3 инстанса хранилища запускались локально (порты: 8080, 8081, 8082).
В качестве профайлера использовалась __jvisualvm__.

## Что было сделано

### Подготовка
Из соображений достоверности результатов, времени необходимого для проведения тестов и достаточности нагрузки 
(в моем понимании нагрузочное тестирование должно быть достаточно "стрессовым" для хранилища) был выбран следующий профиль нагрузуи:
```
load_profile:
  load_type: rps
  schedule: line(1, 500, 20m)
```
Проводил несколько запусков, в результате которых убедился, что при данной нагрузке количество запросов (GET и PUT), выполненных с NET code = 0 (Success) было не меньше чем 91%.

Опытным путем было выявлено, что для того чтобы выдать такую нагрузку не следует ограничивать количесвто инстансов у танка, следовательно:
```
instances: 1000
```

### Подача нагрузки
#### PUT 
Запросы, которыми проводился обстрел находятся в [ammo-файле](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/ammo.txt).

__терминал:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/terminal.png)

__нагрузка на cpu и размер heap:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/cpu_and_mem-usage.png)

__профайлер - cpu:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/profiler-cpu.png)

#### GET

Запросы, которыми проводился обстрел находятся в [ammo-файле](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23/ammo.txt).

__терминал:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23/terminal.png)

__нагрузка на cpu и размер heap:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23/cpu_and_mem-usage.png)

__профайлер - cpu:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23/profiler-cpu.png)

### Оптимизации
Если посмотреть на [результаты профайлера при GET-запросах](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23/profiler-cpu.png),
то мы видим, что больше всего времени мы проводим в функции makeRequest, которая отвечает за отправку http-запросов формата
`/v0/inside?...` другим нодам. 

Я решил оптмизировать данное тонкое место, использую пул http соединений. Для этого функция makeRequest была переписана с использованием
примитивов из `org.apache.http`, вместо `java.net`. 


__До:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/profiler-cpu.png)

__После:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23-with_conn_pool/profiler_cpu-after_optimization.png)
...
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23-with_conn_pool/make_request-info.png)

Видим, что оптимизация оказалась существенной, несмотря на то, что добавилась новая функция - streamRead (второе строка).
Все нормально, она просто считывает entity-запроса.


Также был написан LFU-cache, для ускорения работы GET-запросов.

Странно, но улучшения производительности добиться не удалось.

__До:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23/profiler-cpu.png)

__После:__
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23-with_cache/profiler-cpu.png)

## Возникшие проблемы

Вначале были проблемы с настройкой патронов для танка, поскольку в документации я не нашел информации о том, что после хидеров 
обязательно долны быть симоволы "\r\n\r\n". Но не суть, т.к. в конце-концов стал использовать скрипт с сайта такна.

***

Пул соединений плохо влияет на производительность GET-запросов. Я не понял почему так происходит, но выглядит все следующим образом:

Connection timed out начинают выскакивать буквально на 40-45 запросах в секунду и потом их становиться все больше.
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23-fail_with_conn_pool/terminal.png)

![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/get23-fail_with_conn_pool/profiler-cpu.png)

Поэтому GET-запросы так и остались без пула соединений.

***

К сожалению не удалось, используя утилиту netstat, убедиться, что пул соединений работает так, как ожидается.

Я думал что увижу 100 соединений между нодами 0-1 и 100 соединений между нодами 0-2 и еще энное кол-во запросов,
пытающихся достучаться до нулевой ноды (со стороны танка). Что на практике:

![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/netstat-8080.png)
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/netstat-8081.png)
![](https://github.com/greg-tuzov/2017-highload-kv/blob/master/Ya-tank/Profiler-res/put23/netstat-8082.png)

## Выводы

Оптимизации оказались делом мутным: то, что по идее должно работать быстрее на самом деле может работать также или вовсе хуже.

Наверное, все-таки надо было подавать на сервер меньшую нагрузку (такую, чтобы все запросы завершались штатно).

















