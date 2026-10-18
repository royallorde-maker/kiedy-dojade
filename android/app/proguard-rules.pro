# Compose + org.json — nic nie wymaga własnych reguł; kompresja i shrinkResources
# nie mogą wyciąć modeli danych budowanych z JSON-a (refleksja nie jest używana,
# więc domyślne reguły wystarczają). Trzymamy plik na przyszłość (np. okhttp).
-dontwarn org.jetbrains.annotations.**
