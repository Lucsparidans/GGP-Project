role(a). 
role(b). 
c(1). 
c(2). 
c(3). 
base(d(A,B,f)):-c(A),c(B). 
base(d(A,B,g)):-c(A),c(B). 
base(d(A,B,h)):-c(A),c(B). 
base(i(C)):-role(C). 
input(C,j(A,B)):-c(A),c(B),role(C). 
input(C,k):-role(C). 
init(d(1,1,f)). 
init(d(1,2,f)). 
init(d(1,3,f)). 
init(d(2,1,f)). 
init(d(2,2,f)). 
init(d(2,3,f)). 
init(d(3,1,f)). 
init(d(3,2,f)). 
init(d(3,3,f)). 
init(i(a)). 
next(d(D,E,g)):-does(a,j(D,E)),true(d(D,E,f)). 
next(d(D,E,h)):-does(b,j(D,E)),true(d(D,E,f)). 
next(d(D,E,F)):-true(d(D,E,F)),distinct(F,f). 
next(d(D,E,f)):-does(F,j(G,H)),true(d(D,E,f)),distinct(D,G). 
next(d(D,E,f)):-does(F,j(G,H)),true(d(D,E,f)),distinct(E,H). 
next(i(a)):-true(i(b)). 
next(i(b)):-true(i(a)). 
l(D,A):-true(d(D,1,A)),true(d(D,2,A)),true(d(D,3,A)). 
m(E,A):-true(d(1,E,A)),true(d(2,E,A)),true(d(3,E,A)). 
n(A):-true(d(1,1,A)),true(d(2,2,A)),true(d(3,3,A)). 
n(A):-true(d(1,3,A)),true(d(2,2,A)),true(d(3,1,A)). 
o(A):-l(D,A). 
o(A):-m(D,A). 
o(A):-n(A). 
p:-true(d(D,E,f)). 
legal(F,j(A,B)):-true(d(A,B,f)),true(i(F)). 
legal(a,k):-true(i(b)). 
legal(b,k):-true(i(a)). 
goal(a,100):-o(g). 
goal(a,50):-not(o(g)),not(o(h)),not(p). 
goal(a,0):-o(h). 
goal(b,100):-o(h). 
goal(b,50):-not(o(g)),not(o(h)),not(p). 
goal(b,0):-o(g). 
terminal:-o(g). 
terminal:-o(h). 
terminal:-not(p). 