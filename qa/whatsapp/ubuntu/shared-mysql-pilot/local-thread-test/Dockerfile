FROM eclipse-temurin:21-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends python3 gcc libc6-dev && rm -rf /var/lib/apt/lists/*
WORKDIR /fixture
COPY Hold.java nodump.c launch.c ./
RUN javac Hold.java && jar --create --file /fixture/hold.jar --main-class Hold Hold.class && gcc -shared -fPIC -I$JAVA_HOME/include -I$JAVA_HOME/include/linux nodump.c -o /fixture/libnodump.so && gcc launch.c -o /fixture/launch
COPY thread_check.py test_thread_identity.py integration.py ./
CMD ["python3", "/fixture/integration.py"]
