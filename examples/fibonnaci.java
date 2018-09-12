h input int h = 0b0uuu;
int fib(int a){
	int r = 1;
	if (a > 1){
		r = fib(a - 1) + fib(a - 2);
	}
	return r;
}
l output int o = fib(h);
