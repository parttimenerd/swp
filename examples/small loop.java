bit_width 2;
h input int h = 0b0u;
l input int l = 0bu;
while (l){
  h = [2](h[2] | h[1]);
}
l output int o = h;
