# Pre-Requisites

- Wealthfront Account
- Alpha Vantage API key
- Elasticsearch Instance
- Install SBT

# Running

1) Create `secrets.conf` in `src/main/resources`. Structure it like this:
```HOCON
wealthfront {
  username = "YOUR WEALTHFRONT USERNAME OR EMAIL"
  password = "YOUR WEALTHFRONT PASSWORD"
}
two_fac {
  // This is the secret that wealthfront gives you to generate two factor authentication keys
  secret = "..."
}
alpha_vantage {
  api_key = "YOUR ALPHA VANTAGE API KEY"
}
```
2) Run `sbt run` from the root of this repository


### **Note: I made this for my own personal use. It comes with no guarantees and no support. Please only use it as a reference for your own projects.**
